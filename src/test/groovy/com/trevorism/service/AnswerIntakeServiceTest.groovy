package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.PendingAskStatusType
import com.trevorism.support.FakePromptClient
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.ZoneOffset

import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class AnswerIntakeServiceTest {

    private final TestStore store = new TestStore()
    private final FakePromptClient prompt = new FakePromptClient()
    private final AnswerIntakeService service = new AnswerIntakeService(store.pendingAsks, store.pendingAskRepository, store.metricRepository,
            store.observationService(), store.profileService(), prompt)
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    AnswerIntakeServiceTest() {
        service.clock = Clock.fixed(day(40).toInstant(), ZoneOffset.UTC)
    }

    private GoalPendingAsk ask(GoalMetric metric, String questionId = "q1") {
        store.pendingAskRepository.create(OWNER, new GoalPendingAsk(metricId: metric.id, questionId: questionId,
                periodStart: day(40), periodEnd: day(41), status: PendingAskStatusType.OPEN))
    }

    private GoalMetric metric(Map properties) {
        store.metricService().create(OWNER, root.id, new GoalMetric([name: "m", source: MetricSourceType.PROMPT] + properties))
    }

    private String answer(Map answer, String questionId = "q1") {
        prompt.answers["a1"] = [questionId: questionId, identityId: OWNER] + answer
        service.questionAnswered([questionId: questionId, answerId: "a1", answerText: "spoofed"])
    }

    @Test
    void testAYesNoAnswerIsRecordedForTheAskedPeriod() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        GoalPendingAsk pending = ask(walked)

        assert answer([selectedChoices: ["yes"]]) == AnswerIntakeService.RECORDED

        GoalObservation observation = store.observations.store[0]
        assert [observation.choice, observation.value, observation.observedAt] == ["yes", 1d, day(40)]
        assert observation.metricSource == MetricSourceType.PROMPT
        assert observation.sourceRef == "a1"
        GoalPendingAsk resolved = store.pendingAskRepository.get(OWNER, pending.id)
        assert resolved.status == PendingAskStatusType.ANSWERED
        assert resolved.answerId == "a1"
        assert resolved.resolvedDate == day(40)
    }

    @Test
    void testScaleChoiceNumberAndTextAnswersAreParsed() {
        assert AnswerIntakeService.fill(new GoalObservation(), new GoalMetric(type: MetricType.SCALE), [selectedChoices: ["4"]]) == null
        GoalObservation number = new GoalObservation()
        assert AnswerIntakeService.fill(number, new GoalMetric(type: MetricType.NUMERIC), [text: "about 1,204.5 steps"]) == null
        assert number.value == 1204.5d
        GoalObservation typed = new GoalObservation()
        assert AnswerIntakeService.fill(typed, new GoalMetric(type: MetricType.NUMERIC), [text: "182.5 lb", value: 182.5]) == null
        assert typed.value == 182.5d
        GoalObservation negative = new GoalObservation()
        AnswerIntakeService.fill(negative, new GoalMetric(type: MetricType.NUMERIC), [text: "-3"])
        assert negative.value == -3d
        GoalObservation text = new GoalObservation()
        AnswerIntakeService.fill(text, new GoalMetric(type: MetricType.TEXT), [text: "  slept badly "])
        assert text.label == "slept badly"
        GoalObservation choice = new GoalObservation()
        AnswerIntakeService.fill(choice, new GoalMetric(type: MetricType.CHOICE), [selectedChoices: ["done"]])
        assert choice.choice == "done"
    }

    @Test
    void testAnUnreadableAnswerMarksTheAskInvalid() {
        GoalMetric weight = metric(type: MetricType.NUMERIC)
        GoalPendingAsk pending = ask(weight)

        assert answer([text: "no idea"]) == AnswerIntakeService.INVALID

        GoalPendingAsk resolved = store.pendingAskRepository.get(OWNER, pending.id)
        assert resolved.status == PendingAskStatusType.INVALID
        assert resolved.note == 'Couldn\'t read a number from "no idea"'
        assert store.observations.store.isEmpty()
    }

    @Test
    void testAnAnswerTheMetricRejectsMarksTheAskInvalid() {
        GoalMetric energy = metric(type: MetricType.SCALE, scaleMin: 1, scaleMax: 5)
        ask(energy)

        assert answer([selectedChoices: ["9"]]) == AnswerIntakeService.INVALID
        assert store.pendingAsks.store[0].note.contains("from 1.0 to 5.0")
    }

    @Test
    void testAPeriodAlreadyRecordedByHandIsNotRecordedTwice() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ask(walked)
        store.observationService().create(OWNER, walked.id, new GoalObservation(choice: "no", observedAt: day(40)))

        assert answer([selectedChoices: ["yes"]]) == AnswerIntakeService.DUPLICATE
        assert store.observations.store*.choice == ["no"]
        assert store.pendingAsks.store[0].status == PendingAskStatusType.ANSWERED
    }

    @Test
    void testAnswersAreCheckedWithPromptAndNeverTakenFromTheEvent() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ask(walked)

        prompt.answers["a1"] = [questionId: "q1", identityId: OTHER_OWNER, selectedChoices: ["yes"]]
        assert service.questionAnswered([questionId: "q1", answerId: "a1", selectedChoices: ["yes"]]) == AnswerIntakeService.REJECTED
        prompt.answers["a1"] = [questionId: "other", identityId: OWNER, selectedChoices: ["yes"]]
        assert service.questionAnswered([questionId: "q1", answerId: "a1"]) == AnswerIntakeService.REJECTED
        assert store.observations.store.isEmpty()
        assert store.pendingAsks.store[0].status == PendingAskStatusType.OPEN
    }

    @Test
    void testUnknownQuestionsResolvedAsksAndIncompleteEventsAreIgnored() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ask(walked)

        assert service.questionAnswered([questionId: "unknown", answerId: "a1"]) == AnswerIntakeService.IGNORED
        assert service.questionAnswered([questionId: "q1"]) == AnswerIntakeService.IGNORED
        assert service.questionAnswered(null) == AnswerIntakeService.IGNORED
        answer([selectedChoices: ["yes"]])
        assert answer([selectedChoices: ["no"]]) == AnswerIntakeService.IGNORED
        assert store.observations.store*.choice == ["yes"]
    }

    @Test
    void testADeletedMetricMarksTheAskInvalid() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ask(walked)
        store.metrics.delete(walked.id)

        assert answer([selectedChoices: ["yes"]]) == AnswerIntakeService.INVALID
        assert store.pendingAsks.store[0].note == "The metric no longer exists"
    }

    @Test
    void testDeletingAMetricOrGoalRemovesItsPendingAsks() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ask(walked)
        store.metricService().delete(OWNER, walked.id)
        assert store.pendingAsks.store.isEmpty()

        Goal child = store.goalService().createChild(OWNER, root.id, new Goal(title: "Child"))
        GoalMetric habit = store.metricService().create(OWNER, child.id, new GoalMetric(name: "h", type: MetricType.BOOLEAN))
        ask(habit, "q2")
        store.goalService().delete(OWNER, root.id)
        assert store.pendingAsks.store.isEmpty()
    }
}
