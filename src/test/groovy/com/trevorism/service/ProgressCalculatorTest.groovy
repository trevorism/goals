package com.trevorism.service

import com.trevorism.model.Choice
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.progress.GoalProgress
import com.trevorism.model.progress.MetricProgress
import com.trevorism.model.progress.TreeProgress
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricDirectionType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.ProgressStatusType
import org.junit.jupiter.api.Test

import java.time.LocalDate
import java.time.ZoneOffset

class ProgressCalculatorTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 1)
    private static final Date NOW = day(50)
    private final ProgressCalculator calculator = new ProgressCalculator(NOW)

    private static Date day(int offset) {
        Date.from(START.plusDays(offset).atStartOfDay(ZoneOffset.UTC).toInstant())
    }

    private static Goal goal(String id, String parentId = null, String status = GoalStatusType.ACTIVE) {
        new Goal(id: id, parentId: parentId, title: id, status: status, startDate: day(0), endDate: day(100))
    }

    private static GoalMetric metric(Map properties) {
        new GoalMetric([id: "m", goalId: "g", measures: MetricMeasuresType.OUTCOME, direction: MetricDirectionType.INCREASE, frequency: FrequencyType.DAILY] + properties)
    }

    private static List<GoalObservation> values(Map<Integer, Double> byDay) {
        byDay.collect { offset, value -> new GoalObservation(observedAt: day(offset), value: value) }
    }

    private static List<GoalObservation> answers(Map<Integer, Boolean> byDay) {
        byDay.collect { offset, yes -> new GoalObservation(observedAt: day(offset), value: yes ? 1d : 0d, choice: yes ? "yes" : "no") }
    }

    private MetricProgress progressOf(GoalMetric metric, List<GoalObservation> observations) {
        calculator.metricProgress(metric, goal("g"), observations)
    }

    private static boolean near(Double actual, double expected) {
        actual != null && Math.abs(actual - expected) < 1e-6
    }

    @Test
    void testElapsedFractionIsClampedToTheGoalDates() {
        assert near(calculator.elapsedFraction(goal("g")), 0.5)
        assert near(new ProgressCalculator(day(-10)).elapsedFraction(goal("g")), 0)
        assert near(new ProgressCalculator(day(200)).elapsedFraction(goal("g")), 1)
    }

    @Test
    void testANumericMetricOnPlanIsOnTrackWithAProjection() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)

        MetricProgress result = progressOf(weight, values([0: 200d, 25: 195d, 50: 190d]))

        assert near(result.fitSlope, -0.2)
        assert near(result.fitIntercept, 200)
        assert near(result.fitR2, 1)
        assert result.fitCount == 3
        assert near(result.current, 190)
        assert near(result.score, 0.5)
        assert near(result.expected, 0.5)
        assert near(result.projectedEnd, 180)
        assert near(result.projectedProgress, 1)
        assert result.projectedTargetDate == day(100)
        assert result.status == ProgressStatusType.ON_TRACK
    }

    @Test
    void testAFlatTrendIsBehindAndAFadingOneIsAtRisk() {
        GoalMetric heartRate = metric(type: MetricType.NUMERIC, baseline: 62d, target: 52d, direction: MetricDirectionType.DECREASE)

        MetricProgress flat = progressOf(heartRate, values([0: 62d, 25: 62d, 50: 62d]))
        MetricProgress fading = progressOf(heartRate, values([30: 57d, 40: 56.5d, 50: 56d]))

        assert flat.status == ProgressStatusType.BEHIND
        assert flat.projectedTargetDate == null
        assert near(fading.score, 0.6)
        assert fading.projectedProgress < ProgressCalculator.AT_RISK_PROJECTION
        assert fading.status == ProgressStatusType.AT_RISK
    }

    @Test
    void testFewerThanThreeValuesScoreTheLatestValueWithoutAFit() {
        GoalMetric customers = metric(type: MetricType.NUMERIC, baseline: 0d, target: 10d)

        MetricProgress result = progressOf(customers, values([10: 2d, 40: 7d]))

        assert result.fitSlope == null
        assert result.projectedEnd == null
        assert near(result.current, 7)
        assert near(result.score, 0.7)
        assert result.status == ProgressStatusType.AHEAD
    }

    @Test
    void testAMissingBaselineStartsFromTheFirstValueAndAZeroBaselineIsKept() {
        assert near(progressOf(metric(type: MetricType.NUMERIC, target: 20d), values([0: 10d, 40: 15d])).score, 0.5)
        assert near(progressOf(metric(type: MetricType.NUMERIC, baseline: 0d, target: 20d), values([0: 10d, 40: 15d])).score, 0.75)
    }

    @Test
    void testANumericMetricWithoutATargetIsNotScored() {
        MetricProgress result = progressOf(metric(type: MetricType.NUMERIC), values([0: 1d, 10: 2d, 20: 3d]))

        assert result.score == null
        assert result.fitSlope != null
        assert result.status == ProgressStatusType.NO_DATA
    }

    @Test
    void testAMaintainMetricScoresTheShareOfRecentValuesWithinTolerance() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, direction: MetricDirectionType.MAINTAIN, target: 180d, tolerance: 2d)

        MetricProgress result = progressOf(weight, values([1: 170d, 10: 179d, 20: 181d, 30: 186d, 40: 180d]))

        assert near(result.score, 0.6)
        assert near(result.expected, 1)
        assert result.status == ProgressStatusType.BEHIND
    }

    @Test
    void testAScaleMetricScoresItsRollingMeanAgainstTheScale() {
        GoalMetric energy = metric(type: MetricType.SCALE, scaleMin: 1d, scaleMax: 5d)

        MetricProgress result = progressOf(energy, values([10: 3d, 20: 3d, 30: 4d, 40: 4d]))

        assert near(result.current, 3.5)
        assert near(result.score, 0.625)
        assert result.fitSlope > 0
        assert near(result.projectedEnd, 5)
    }

    @Test
    void testAYesNoMetricScoresAdherenceOverTheLastFourWeeksAgainstItsTarget() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN, target: 0.8d)
        Map<Integer, Boolean> byDay = (23..49).collectEntries { [it, it % 3 != 0] }

        MetricProgress result = progressOf(walked, answers(byDay))

        int yes = (23..49).count { it % 3 != 0 }
        assert near(result.adherence, yes / 27d)
        assert near(result.score, Math.min(yes / 27d / 0.8d, 1d))
        assert near(result.expected, 1)
    }

    @Test
    void testUnansweredDaysCountAsNoButAnUnansweredTodayDoesNot() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([44: true, 46: true, 49: true]))

        assert near(result.adherence, 3 / 6d)
        assert result.currentStreak == 1
        assert result.bestStreak == 1
    }

    @Test
    void testStreaksCountConsecutiveYesPeriods() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([40: true, 41: true, 42: true, 43: false, 47: true, 48: true, 49: true, 50: true]))

        assert result.currentStreak == 4
        assert result.bestStreak == 4
        assert progressOf(walked, answers([40: true, 41: true, 42: true, 48: true, 49: true])).currentStreak == 2
    }

    @Test
    void testAWeeklyYesNoMetricCountsWeeks() {
        GoalMetric zone2 = metric(type: MetricType.BOOLEAN, frequency: FrequencyType.WEEKLY)

        MetricProgress result = progressOf(zone2, answers([24: true, 31: true, 38: false, 45: true]))

        assert near(result.adherence, 0.75)
        assert result.bestStreak == 2
    }

    @Test
    void testTheLatestAnswerForAPeriodWins() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, [
                new GoalObservation(observedAt: day(49), value: 0d, createdDate: day(49)),
                new GoalObservation(observedAt: day(49), value: 1d, createdDate: day(50))
        ])

        assert near(result.adherence, 1)
    }

    @Test
    void testAChoiceMetricScoresTheMeanPositionOfRecentAnswers() {
        GoalMetric meals = metric(type: MetricType.CHOICE, choices: [new Choice(value: "poor"), new Choice(value: "ok"), new Choice(value: "good")])

        MetricProgress result = progressOf(meals, values([40: 0d, 45: 2d, 50: 2d]))

        assert near(result.score, 2 / 3d)
        assert result.status == ProgressStatusType.BEHIND
    }

    @Test
    void testTextMetricsAndMetricsWithoutValuesHaveNoData() {
        assert progressOf(metric(type: MetricType.TEXT), [new GoalObservation(observedAt: day(10), label: "note")]).status == ProgressStatusType.NO_DATA
        MetricProgress empty = progressOf(metric(type: MetricType.NUMERIC, target: 10d), [])
        assert empty.status == ProgressStatusType.NO_DATA
        assert empty.observationCount == 0
    }

    @Test
    void testFutureAndMissedObservationsAreIgnoredForTrends() {
        GoalMetric customers = metric(type: MetricType.NUMERIC, baseline: 0d, target: 10d)

        MetricProgress result = progressOf(customers, values([10: 5d, 90: 10d]) + [new GoalObservation(observedAt: day(20), missed: true)])

        assert near(result.score, 0.5)
        assert result.observationCount == 1
    }

    @Test
    void testAGoalHeadlinesItsOutcomeMetricsAndRollsEffortFromItsChildren() {
        Goal root = goal("root")
        Goal done = goal("done", "root", GoalStatusType.COMPLETED)
        Goal idle = goal("idle", "root")
        Goal dropped = goal("dropped", "root", GoalStatusType.ABANDONED)
        GoalMetric weight = metric(id: "weight", goalId: "root", type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)

        TreeProgress tree = calculator.calculate(root, [root, done, idle, dropped], [weight], [weight: values([0: 200d, 25: 195d, 50: 190d])])
        Map<String, GoalProgress> byId = tree.goals.collectEntries { [it.goalId, it] }

        assert near(byId.root.outcomeProgress, 0.5)
        assert near(byId.root.progress, 0.5)
        assert byId.root.status == ProgressStatusType.ON_TRACK
        assert near(byId.root.effortProgress, 0.5)
        assert near(byId.done.progress, 1)
        assert byId.idle.progress == null
        assert byId.idle.status == ProgressStatusType.NO_DATA
        assert tree.goals.size() == 4
        assert tree.asOf == NOW
    }

    @Test
    void testWithoutOutcomeMetricsAGoalHeadlinesItsEffort() {
        Goal root = goal("root")
        Goal cardio = goal("cardio", "root")
        GoalMetric walked = metric(id: "walked", goalId: "cardio", type: MetricType.BOOLEAN, measures: MetricMeasuresType.EFFORT)
        Map<Integer, Boolean> everyDay = (23..50).collectEntries { [it, true] }

        TreeProgress tree = calculator.calculate(root, [root, cardio], [walked], [walked: answers(everyDay)])
        Map<String, GoalProgress> byId = tree.goals.collectEntries { [it.goalId, it] }

        assert byId.cardio.outcomeProgress == null
        assert near(byId.cardio.effortProgress, 1)
        assert near(byId.cardio.pace, 0)
        assert byId.cardio.status == ProgressStatusType.ON_TRACK
        assert near(byId.root.progress, 1)
        assert byId.root.status == ProgressStatusType.ON_TRACK
    }

    @Test
    void testAnUnmeasuredSubGoalPullsItsParentBehindAsTimePasses() {
        Goal root = goal("root")

        TreeProgress tree = calculator.calculate(root, [root, goal("step", "root")], [], [:])
        GoalProgress rootProgress = tree.goals.find { it.goalId == "root" }

        assert near(rootProgress.progress, 0)
        assert near(rootProgress.pace, -0.5)
        assert rootProgress.status == ProgressStatusType.BEHIND
    }

    @Test
    void testAGoalWithAnAtRiskOutcomeIsAtRisk() {
        Goal root = goal("root")
        GoalMetric heartRate = metric(id: "hr", goalId: "root", type: MetricType.NUMERIC, baseline: 62d, target: 52d, direction: MetricDirectionType.DECREASE)

        TreeProgress tree = calculator.calculate(root, [root], [heartRate], [hr: values([30: 57d, 40: 56.5d, 50: 56d])])

        assert tree.goals[0].status == ProgressStatusType.AT_RISK
    }

    @Test
    void testOnlyTheRequestedSubtreeIsCalculated() {
        Goal root = goal("root")
        Goal cardio = goal("cardio", "root")
        GoalMetric elsewhere = metric(id: "x", goalId: "other", type: MetricType.NUMERIC, target: 1d)

        TreeProgress tree = calculator.calculate(cardio, [cardio], [elsewhere], [:])

        assert tree.goals*.goalId == ["cardio"]
        assert tree.metrics.isEmpty()
    }
}
