package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.dashboard.Dashboard
import com.trevorism.model.dashboard.NeedsYouItem
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricDirectionType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.NeedsYouType
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
    private final DashboardService service = new DashboardService(store.goalRepository, store.metricRepository, progressService)
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
        store.goalService().createChild(OWNER, health.id, new Goal(title: "Cardio", startDate: day(10), endDate: day(40)))
        store.goalService().update(OWNER, health.id, new Goal(status: GoalStatusType.ABANDONED))

        assert service.dashboard(OWNER, isoDay(41)).needsYou.isEmpty()
    }
}
