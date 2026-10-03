package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.progress.MetricSegment
import com.trevorism.model.progress.TreeProgress
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricDirectionType
import com.trevorism.model.types.MetricType
import org.junit.jupiter.api.Test

import java.time.LocalDate
import java.time.ZoneOffset

class SegmentTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 1)
    private final ProgressCalculator calculator = new ProgressCalculator(day(60))
    private final Goal goal = new Goal(id: "g", title: "g", status: GoalStatusType.ACTIVE, startDate: day(0), endDate: day(100))

    private static Date day(int offset) {
        Date.from(START.plusDays(offset).atStartOfDay(ZoneOffset.UTC).toInstant())
    }

    private static GoalAdjustment adjustment(String id, int offset) {
        new GoalAdjustment(id: id, goalId: "g", title: "change ${id}".toString(), effectiveDate: day(offset))
    }

    private static List<GoalObservation> line(IntRange days, double startValue, double perDay) {
        days.collect { new GoalObservation(observedAt: day(it), value: startValue + perDay * (it - days.from)) }
    }

    @Test
    void testATrendSplitsAtEachAdjustmentWithItsOwnSlope() {
        GoalMetric weight = new GoalMetric(id: "w", goalId: "g", type: MetricType.NUMERIC, direction: MetricDirectionType.DECREASE)
        List<GoalObservation> observations = line(0..29, 200d, -0.1d) + line(30..60, 197d, -0.3d)

        List<MetricSegment> segments = calculator.segments(weight, goal, observations, [adjustment("a", 30)])

        assert segments*.adjustmentId == [null, "a"]
        assert segments*.adjustmentTitle == [null, "change a"]
        assert segments*.startDate == [day(0), day(30)]
        assert segments*.endDate == [day(30), day(60)]
        assert segments*.count == [30, 31]
        assert Math.abs(segments[0].slope + 0.1) < 1e-9
        assert Math.abs(segments[1].slope + 0.3) < 1e-9
    }

    @Test
    void testASegmentWithFewerThanFivePointsHasNoSlope() {
        GoalMetric weight = new GoalMetric(id: "w", goalId: "g", type: MetricType.NUMERIC)

        List<MetricSegment> segments = calculator.segments(weight, goal, line(0..9, 1d, 1d) + line(57..60, 20d, 1d), [adjustment("a", 57)])

        assert segments*.count == [10, 4]
        assert segments[0].slope != null
        assert segments[1].slope == null
    }

    @Test
    void testYesNoSegmentsCompareAdherence() {
        GoalMetric walked = new GoalMetric(id: "y", goalId: "g", type: MetricType.BOOLEAN, frequency: FrequencyType.DAILY)
        List<GoalObservation> answers = (40..59).collect { int offset ->
            boolean yes = offset < 50 ? offset % 2 == 0 : true
            new GoalObservation(observedAt: day(offset), value: yes ? 1d : 0d)
        }

        List<MetricSegment> segments = calculator.segments(walked, goal, answers, [adjustment("a", 50)])

        assert segments*.count == [10, 10]
        assert segments[0].adherence == 0.5d
        assert segments[1].adherence == 1d
        assert segments*.slope == [null, null]
    }

    @Test
    void testOnlyAdjustmentsAfterTheStartAndUpToNowSplitTheHistory() {
        GoalMetric weight = new GoalMetric(id: "w", goalId: "g", type: MetricType.NUMERIC)

        assert calculator.segments(weight, goal, line(0..60, 1d, 1d), [adjustment("before", 0), adjustment("future", 70)]).isEmpty()
        assert calculator.segments(new GoalMetric(id: "c", type: MetricType.CHOICE), goal, [], [adjustment("a", 30)]).isEmpty()
        assert calculator.segments(new GoalMetric(id: "t", type: MetricType.TEXT), goal, [], [adjustment("a", 30)]).isEmpty()
    }

    @Test
    void testAdjustmentsAreOrderedByDate() {
        GoalMetric weight = new GoalMetric(id: "w", goalId: "g", type: MetricType.NUMERIC)

        List<MetricSegment> segments = calculator.segments(weight, goal, line(0..60, 1d, 1d), [adjustment("late", 45), adjustment("early", 20)])

        assert segments*.adjustmentId == [null, "early", "late"]
    }

    @Test
    void testTheTreeResultCarriesSegmentsForMetricsWithAdjustments() {
        GoalMetric weight = new GoalMetric(id: "w", goalId: "g", type: MetricType.NUMERIC)
        GoalMetric untouched = new GoalMetric(id: "u", goalId: "g", type: MetricType.NUMERIC)

        TreeProgress tree = calculator.calculate(goal, [goal], [weight, untouched],
                [w: line(0..60, 1d, 1d), u: line(0..60, 1d, 1d)], [w: [adjustment("a", 30)]])

        assert tree.segments*.metricId == ["w", "w"]
    }

    @Test
    void testScopeAppliesTargetedAdjustmentsToTheirMetricsAndOthersToTheGoalsSubtree() {
        Goal root = new Goal(id: "root")
        Goal cardio = new Goal(id: "cardio", parentId: "root")
        Goal zone2 = new Goal(id: "zone2", parentId: "cardio")
        Goal sleep = new Goal(id: "sleep", parentId: "root")
        GoalMetric sessions = new GoalMetric(id: "sessions", goalId: "zone2")
        GoalMetric hours = new GoalMetric(id: "hours", goalId: "sleep")
        GoalAdjustment onCardio = new GoalAdjustment(id: "bike", goalId: "cardio", metricIds: [])
        GoalAdjustment onRoot = new GoalAdjustment(id: "diet", goalId: "root")
        GoalAdjustment targeted = new GoalAdjustment(id: "mask", goalId: "root", metricIds: ["hours"])

        Map<String, List<GoalAdjustment>> scoped = AdjustmentScope.byMetric([sessions, hours], [onCardio, onRoot, targeted], [root, cardio, zone2, sleep])

        assert scoped.sessions*.id == ["bike", "diet"]
        assert scoped.hours*.id == ["diet", "mask"]
    }
}
