package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.today.TodayItem
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.GoalStatusType
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.LocalDate

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class TodayServiceTest {

    private final TestStore store = new TestStore()
    private final TodayService service = new TodayService(store.goalRepository, store.metricRepository)
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    private GoalMetric metric(String name, String frequency, Goal goal = root) {
        store.metricService().create(OWNER, goal.id, new GoalMetric(name: name, frequency: frequency))
    }

    private void record(GoalMetric metric, int dayOfYear) {
        store.observationService().create(OWNER, metric.id, new GoalObservation(value: 1, observedAt: day(dayOfYear)))
    }

    private static String isoDay(int dayOfYear) {
        LocalDate.ofYearDay(2027, dayOfYear).toString()
    }

    @Test
    void testMetricsWithNothingRecordedThisPeriodAreDue() {
        GoalMetric weight = metric("Weight", FrequencyType.DAILY)
        GoalMetric zone2 = metric("Zone 2", FrequencyType.WEEKLY)
        GoalMetric checkup = metric("Checkup", FrequencyType.MONTHLY)
        record(weight, 40)
        record(zone2, 39)
        record(checkup, 33)

        List<TodayItem> due = service.due(OWNER, isoDay(41))

        assert due*.metric*.name == ["Weight"]
        assert due[0].goalTitle == "Improve healthspan"
        assert due[0].rootTitle == "Improve healthspan"
        assert due[0].rootId == root.id
        assert due[0].periodStart == day(41)
        assert service.due(OWNER, isoDay(40)).isEmpty()
        assert service.due(OWNER, isoDay(46))*.metric*.name == ["Weight", "Zone 2"]
    }

    @Test
    void testNeverRecordedMetricsAreDueAndSortedByTreeThenGoalThenName() {
        Goal cardio = store.goalService().createChild(OWNER, root.id, new Goal(title: "Cardio"))
        metric("Walked", FrequencyType.DAILY, cardio)
        metric("Weight", FrequencyType.DAILY)
        metric("Energy", FrequencyType.DAILY, cardio)

        assert service.due(OWNER, isoDay(10)).collect { "${it.goalTitle}/${it.metric.name}".toString() } ==
                ["Cardio/Energy", "Cardio/Walked", "Improve healthspan/Weight"]
    }

    @Test
    void testGoalsThatAreClosedOrOutsideTheirDatesAreSkipped() {
        Goal cardio = store.goalService().createChild(OWNER, root.id, new Goal(title: "Cardio", startDate: day(20), endDate: day(30)))
        metric("Walked", FrequencyType.DAILY, cardio)
        Goal dropped = store.goalService().createRoot(OWNER, rootGoal("Dropped"))
        metric("Old habit", FrequencyType.DAILY, dropped)
        store.goalService().update(OWNER, dropped.id, new Goal(status: GoalStatusType.ABANDONED))

        assert service.due(OWNER, isoDay(10)).isEmpty()
        assert service.due(OWNER, isoDay(25))*.metric*.name == ["Walked"]

        store.goalService().update(OWNER, root.id, new Goal(status: GoalStatusType.COMPLETED))
        assert service.due(OWNER, isoDay(25)).isEmpty()
    }

    @Test
    void testDisabledAndOtherOwnersMetricsAreSkipped() {
        GoalMetric paused = metric("Paused", FrequencyType.DAILY)
        store.metricService().update(OWNER, paused.id, new GoalMetric(enabled: false))
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal())
        store.metricService().create(OTHER_OWNER, theirs.id, new GoalMetric(name: "Theirs"))

        assert service.due(OWNER, isoDay(10)).isEmpty()
    }

    @Test
    void testTheDateMustBeACalendarDay() {
        assertBadRequest { service.due(OWNER, "10/03/2027") }
        assert service.due(OWNER, null) != null
    }
}
