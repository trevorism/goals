package com.trevorism.service

import com.trevorism.model.Choice
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.GoalProfile
import com.trevorism.model.collection.CollectionResult
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.PendingAskStatusType
import com.trevorism.support.FakePromptClient
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class CollectionServiceTest {

    private static final Instant NOON_DAY_40 = day(40).toInstant().plusSeconds(12 * 3600)

    private final TestStore store = new TestStore()
    private final FakePromptClient prompt = new FakePromptClient()
    private final CollectionService service = new CollectionService(store.metrics, store.goals, store.pendingAsks, store.pendingAskRepository,
            store.observationService(), store.profileService(), prompt)
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    CollectionServiceTest() {
        at(NOON_DAY_40)
    }

    private void at(Instant instant) {
        service.clock = Clock.fixed(instant, ZoneOffset.UTC)
    }

    private GoalMetric promptMetric(Map properties = [:], String owner = OWNER, Goal goal = root) {
        store.metricService().create(owner, goal.id, new GoalMetric([name: "Walked", type: MetricType.BOOLEAN, source: MetricSourceType.PROMPT] + properties))
    }

    @Test
    void testTheTickAsksOnePrivateQuestionPerPromptMetricAndPeriod() {
        GoalMetric walked = promptMetric()
        store.metricService().create(OWNER, root.id, new GoalMetric(name: "Manual weight"))

        CollectionResult first = service.tick()
        CollectionResult second = service.tick()

        assert first.asked == 1
        assert second.asked == 0
        assert prompt.questions.size() == 1
        Map question = prompt.questions[0]
        assert question.targetIdentityId == OWNER
        assert question.privateQuestion == true
        assert question.kind == "question"
        assert question.choices*.value == ["yes", "no"]
        assert question.dueDate == day(41).toInstant().toString()
        assert question.text == "Improve healthspan: Walked — Tue, Feb 9"
        GoalPendingAsk ask = store.pendingAsks.store[0]
        assert [ask.ownerId, ask.metricId, ask.questionId, ask.status] == [OWNER, walked.id, "q1", PendingAskStatusType.OPEN]
        assert ask.periodStart == day(40)
        assert ask.periodEnd == day(41)
    }

    @Test
    void testPeriodsFollowTheOwnersTimezone() {
        store.profileService().update(OWNER, new GoalProfile(timezone: "America/New_York"))
        promptMetric()
        at(day(40).toInstant().plusSeconds(2 * 3600))

        service.tick()

        GoalPendingAsk ask = store.pendingAsks.store[0]
        assert ask.periodStart.toInstant() == day(39).toInstant().plusSeconds(5 * 3600)
        assert prompt.questions[0].text.endsWith("Mon, Feb 8")
    }

    @Test
    void testAWeeklyMetricIsAskedOncePerWeek() {
        promptMetric(frequency: FrequencyType.WEEKLY)

        service.tick()
        at(NOON_DAY_40.plusSeconds(2 * 86400))
        service.tick()
        at(NOON_DAY_40.plusSeconds(6 * 86400))
        service.tick()

        assert prompt.questions*.text == ["Improve healthspan: Walked — week of Feb 8", "Improve healthspan: Walked — week of Feb 15"]
    }

    @Test
    void testAMetricAlreadyRecordedThisPeriodIsNotAsked() {
        GoalMetric walked = promptMetric()
        store.observationService().create(OWNER, walked.id, new GoalObservation(choice: "yes", observedAt: day(40)))

        CollectionResult result = service.tick()

        assert result.asked == 0
        assert result.alreadyRecorded == 1
    }

    @Test
    void testClosedGoalsDisabledMetricsAndOutOfRangeDatesAreNotAsked() {
        Goal later = store.goalService().createChild(OWNER, root.id, new Goal(title: "Later", startDate: day(50), endDate: day(60)))
        promptMetric([:], OWNER, later)
        Goal dropped = store.goalService().createRoot(OWNER, rootGoal("Dropped"))
        promptMetric([:], OWNER, dropped)
        store.goalService().update(OWNER, dropped.id, new Goal(status: GoalStatusType.ABANDONED))
        GoalMetric paused = promptMetric()
        store.metricService().update(OWNER, paused.id, new GoalMetric(enabled: false))

        assert service.tick().asked == 0
    }

    @Test
    void testEachOwnersMetricsAreAskedOfThatOwner() {
        promptMetric()
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal("Theirs"))
        promptMetric([:], OTHER_OWNER, theirs)

        service.tick()

        assert prompt.questions*.targetIdentityId.toSorted() == [OTHER_OWNER, OWNER].toSorted()
        assert store.pendingAsks.store*.ownerId.toSorted() == [OTHER_OWNER, OWNER].toSorted()
    }

    @Test
    void testAnExpiredOpenAskBecomesMissedWithAMissedValue() {
        GoalMetric walked = promptMetric()
        service.tick()
        at(NOON_DAY_40.plusSeconds(86400))

        CollectionResult result = service.tick()

        assert result.missed == 1
        assert result.asked == 1
        GoalPendingAsk expired = store.pendingAsks.store.find { it.periodStart == day(40) }
        assert expired.status == PendingAskStatusType.MISSED
        assert expired.resolvedDate
        GoalObservation missed = store.observations.store.find { it.metricId == walked.id }
        assert missed.missed
        assert missed.observedAt == day(40)
        assert missed.metricSource == MetricSourceType.PROMPT
        assert missed.sourceRef == "q1"
    }

    @Test
    void testOnePromptFailureDoesNotStopTheTick() {
        promptMetric()
        prompt.failing = true

        CollectionResult result = service.tick()

        assert result.failed == 1
        assert store.pendingAsks.store.isEmpty()
    }

    @Test
    void testQuestionsCarryTheChoicesForEachType() {
        Goal sleep = store.goalService().createChild(OWNER, root.id, new Goal(title: "Sleep"))
        promptMetric([name: "Energy", type: MetricType.SCALE, scaleMin: 1, scaleMax: 5], OWNER, sleep)
        promptMetric([name: "Routine", type: MetricType.CHOICE, choices: [new Choice(label: "Skipped"), new Choice(label: "Done")]], OWNER, sleep)
        promptMetric([name: "Weight", type: MetricType.NUMERIC, unit: "lb", promptText: "What did the scale say?"], OWNER, sleep)

        service.tick()

        assert prompt.questions.find { it.text.startsWith("Sleep: Energy") }.choices*.value == ["1", "2", "3", "4", "5"]
        assert prompt.questions.find { it.text.startsWith("Sleep: Routine") }.choices*.value == ["skipped", "done"]
        Map weight = prompt.questions.find { it.text.startsWith("What did the scale say?") }
        assert weight.choices == []
        assert weight.text == "What did the scale say? — Tue, Feb 9. Reply with a number."
        assert prompt.questions.size() == 3
    }
}
