package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.dashboard.Dashboard
import com.trevorism.model.dashboard.NeedsYouItem
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricDirectionType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.NeedsYouType
import com.trevorism.model.types.PendingAskStatusType
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class DashboardServiceTest {

    private final TestStore store = new TestStore()
    private final ProgressService progressService = new ProgressService(store.goalRepository, store.metricRepository, store.observationRepository, store.adjustmentRepository)
    private final DashboardService service = new DashboardService(store.goalRepository, store.metricRepository, store.pendingAskRepository,
            progressService, store.profileService())
    private final Goal health = store.goalService().createRoot(OWNER, rootGoal())

    DashboardServiceTest() {
        progressService.clock = Clock.fixed(day(183).toInstant(), ZoneOffset.UTC)
    }

    private static String isoDay(int dayOfYear) {
        LocalDate.ofYearDay(2027, dayOfYear).toString()
    }

    private GoalMetric metric(Goal goal, Map properties) {
        store.metricService().create(OWNER, goal.id, new GoalMetric([name: "m"] + properties))
    }

    private void ask(GoalMetric metric, int dayOfYear, String status, String note = null) {
        store.pendingAskRepository.create(OWNER, new GoalPendingAsk(metricId: metric.id, questionId: "q${dayOfYear}".toString(),
                periodStart: day(dayOfYear), periodEnd: day(dayOfYear + 1), status: status, note: note))
    }

    private List<NeedsYouItem> needs(String type, Dashboard dashboard) {
        dashboard.needsYou.findAll { it.type == type }
    }

    @Test
    void eachRootGoalShowsItsProgressAndItsMostConcerningOutcomeMetric() {
        GoalMetric weight = metric(health, [name: "Weight", baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE])
        GoalMetric heartRate = metric(health, [name: "Heart rate", baseline: 62d, target: 52d, direction: MetricDirectionType.DECREASE])
        metric(health, [name: "Walked", type: MetricType.BOOLEAN, measures: MetricMeasuresType.EFFORT])
        [[weight, 200d], [weight, 199d], [heartRate, 60d], [heartRate, 55d]].eachWithIndex { pair, index ->
            store.observationService().create(OWNER, pair[0].id, new GoalObservation(value: pair[1], observedAt: day(100 + index * 40)))
        }
        store.goalService().createChild(OWNER, health.id, new Goal(title: "Cardio"))

        Dashboard dashboard = service.dashboard(OWNER, isoDay(183))

        assert dashboard.goals*.goal*.title == ["Improve healthspan"]
        assert dashboard.goals[0].progress.goalId == health.id
        assert dashboard.goals[0].progress.status
        assert dashboard.goals[0].headlineMetric.name == "Weight"
        assert dashboard.goals[0].headlineMetricProgress.metricId == weight.id
        assert dashboard.asOf
    }

    @Test
    void activeGoalsComeFirstThenByEndDate() {
        Goal customers = store.goalService().createRoot(OWNER, new Goal(title: "Paying customers", startDate: day(1), endDate: day(200)))
        Goal done = store.goalService().createRoot(OWNER, new Goal(title: "Done already", startDate: day(1), endDate: day(50)))
        store.goalService().update(OWNER, done.id, new Goal(status: GoalStatusType.COMPLETED))
        store.goalService().createRoot(OTHER_OWNER, rootGoal("Not mine"))

        Dashboard dashboard = service.dashboard(OWNER, isoDay(100))

        assert dashboard.goals*.goal*.title == ["Paying customers", "Improve healthspan", "Done already"]
        assert dashboard.goals[0].headlineMetric == null
        assert customers.id == dashboard.goals[0].goal.id
    }

    @Test
    void unreadableAnswersNeedYouUntilAValueIsRecordedForThatPeriod() {
        GoalMetric weight = metric(health, [name: "Weight", source: MetricSourceType.PROMPT])
        ask(weight, 100, PendingAskStatusType.INVALID, 'Couldn\'t read a number from "lots"')

        NeedsYouItem item = needs(NeedsYouType.UNREADABLE_ANSWER, service.dashboard(OWNER, isoDay(101)))[0]
        assert [item.title, item.detail, item.metricId, item.goalId, item.rootId, item.date] ==
                ["Weight", 'Couldn\'t read a number from "lots"', weight.id, health.id, health.id, day(100)]

        store.observationService().create(OWNER, weight.id, new GoalObservation(value: 190, observedAt: day(100)))
        assert needs(NeedsYouType.UNREADABLE_ANSWER, service.dashboard(OWNER, isoDay(101))).isEmpty()
    }

    @Test
    void threeOrMoreMissedQuestionsInARowNeedYou() {
        GoalMetric walked = metric(health, [name: "Walked", type: MetricType.BOOLEAN, source: MetricSourceType.PROMPT])
        GoalMetric sleep = metric(health, [name: "Sleep", type: MetricType.BOOLEAN, source: MetricSourceType.PROMPT])
        [10, 11].each { ask(walked, it, PendingAskStatusType.ANSWERED) }
        [12, 13, 14, 15].each { ask(walked, it, PendingAskStatusType.MISSED) }
        ask(walked, 16, PendingAskStatusType.OPEN)
        [12, 13].each { ask(sleep, it, PendingAskStatusType.MISSED) }
        ask(sleep, 14, PendingAskStatusType.ANSWERED)
        [15, 16].each { ask(sleep, it, PendingAskStatusType.MISSED) }

        List<NeedsYouItem> missed = needs(NeedsYouType.MISSED_QUESTIONS, service.dashboard(OWNER, isoDay(16)))

        assert missed*.title == ["Walked"]
        assert missed[0].count == 4
        assert missed[0].date == day(15)
    }

    @Test
    void activeGoalsPastTheirEndDateNeedYou() {
        Goal cardio = store.goalService().createChild(OWNER, health.id, new Goal(title: "Cardio", startDate: day(10), endDate: day(40)))
        Goal sleep = store.goalService().createChild(OWNER, health.id, new Goal(title: "Sleep", startDate: day(10), endDate: day(40)))
        store.goalService().update(OWNER, sleep.id, new Goal(status: GoalStatusType.COMPLETED))

        assert needs(NeedsYouType.PAST_END_DATE, service.dashboard(OWNER, isoDay(40))).isEmpty()
        List<NeedsYouItem> late = needs(NeedsYouType.PAST_END_DATE, service.dashboard(OWNER, isoDay(41)))
        assert late*.goalId == [cardio.id]
        assert late[0].rootId == health.id
        assert late[0].date == day(40)
    }

    @Test
    void closedGoalsDoNotNeedYou() {
        GoalMetric weight = metric(health, [name: "Weight", source: MetricSourceType.PROMPT])
        ask(weight, 100, PendingAskStatusType.INVALID, "unreadable")
        [101, 102, 103].each { ask(weight, it, PendingAskStatusType.MISSED) }
        store.goalService().update(OWNER, health.id, new Goal(status: GoalStatusType.ABANDONED))

        assert service.dashboard(OWNER, isoDay(366 - 1)).needsYou.isEmpty()
    }
}
