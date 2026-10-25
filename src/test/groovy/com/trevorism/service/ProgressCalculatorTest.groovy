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

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
    void testUnrecordedDaysAreLeftOutOfAdherenceAndReportedAsCoverage() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([44: true, 46: false, 49: true]))

        assert near(result.adherence, 2 / 3d)
        assert result.answeredPeriods == 3
        assert result.windowPeriods == 6
        assert result.currentStreak == 1
        assert result.bestStreak == 1
    }

    @Test
    void testMissedAnswersCountAsUnrecordedRatherThanNo() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        List<GoalObservation> observations = answers([47: true, 49: true]) + [new GoalObservation(observedAt: day(48), missed: true)]

        MetricProgress result = progressOf(walked, observations)

        assert near(result.adherence, 1)
        assert result.answeredPeriods == 2
        assert result.observationCount == 2
    }

    @Test
    void testAnswersOlderThanTheWindowLeaveAdherenceWithoutData() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([5: true, 6: true]))

        assert result.adherence == null
        assert result.score == null
        assert result.answeredPeriods == 0
        assert result.status == ProgressStatusType.NO_DATA
    }

    @Test
    void testStreaksCountConsecutiveYesPeriods() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([40: true, 41: true, 42: true, 43: false, 47: true, 48: true, 49: true, 50: true]))

        assert result.currentStreak == 4
        assert result.bestStreak == 4
    }

    @Test
    void testUnrecordedDaysDoNotBreakAStreak() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)

        MetricProgress result = progressOf(walked, answers([40: true, 41: true, 42: true, 48: true, 49: true]))

        assert result.currentStreak == 5
        assert result.bestStreak == 5
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
        assert near(byId.root.effortProgress, 1)
        assert byId.root.subGoalsWithoutData == 1
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
    void testSubGoalsWithoutDataAreLeftOutAndOnlyStartedOnesAreCounted() {
        Goal root = goal("root")
        Goal later = new Goal(id: "later", parentId: "root", title: "later", status: GoalStatusType.ACTIVE, startDate: day(60), endDate: day(90))

        TreeProgress tree = calculator.calculate(root, [root, goal("step", "root"), later], [], [:])
        GoalProgress rootProgress = tree.goals.find { it.goalId == "root" }

        assert rootProgress.progress == null
        assert rootProgress.pace == null
        assert rootProgress.status == ProgressStatusType.NO_DATA
        assert rootProgress.subGoalsWithoutData == 1
    }

    @Test
    void testAPausedMetricIsShownButLeftOutOfItsGoalsProgress() {
        Goal root = goal("root")
        GoalMetric weight = metric(id: "weight", goalId: "root", type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)
        GoalMetric fees = metric(id: "fees", goalId: "root", type: MetricType.NUMERIC, baseline: 0d, target: 100d, enabled: false)

        TreeProgress tree = calculator.calculate(root, [root], [weight, fees], [weight: values([0: 200d, 25: 195d, 50: 190d]), fees: values([50: 0d])])

        assert near(tree.goals[0].outcomeProgress, 0.5)
        assert near(tree.metrics.find { it.metricId == "fees" }.score, 0)
    }

    @Test
    void testATrendReportsTheRequiredRateAndTheRecentRate() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, baseline: 215d, target: 200d, direction: MetricDirectionType.DECREASE)
        List<GoalObservation> observations = values([0: 215d, 10: 214d, 20: 213d, 36: 213d, 40: 213d, 44: 213d, 47: 213d, 50: 213d])

        MetricProgress result = progressOf(weight, observations)

        assert near(result.requiredRate, -13d / (50d / 7d))
        assert near(result.recentRate, 0)
        assert result.stalled
    }

    @Test
    void testATrendOnPaceIsNotStalled() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)
        List<GoalObservation> observations = values((0..10).collectEntries { [it * 5, 200d - it * 2d] })

        MetricProgress result = progressOf(weight, observations)

        assert near(result.recentRate, -2.8)
        assert result.requiredRate < 0
        assert !result.stalled
    }

    @Test
    void testRatesNeedEnoughValuesAndAnOpenTarget() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)

        MetricProgress sparse = progressOf(weight, values([40: 195d, 50: 194d]))
        MetricProgress reached = progressOf(weight, values([30: 185d, 35: 182d, 40: 180d, 45: 179d, 50: 178d]))

        assert sparse.recentRate == null
        assert sparse.stalled == null
        assert sparse.requiredRate != null
        assert reached.requiredRate == null
        assert reached.stalled == null
    }

    @Test
    void testValuesOnASingleDayHaveNoRecentRate() {
        GoalMetric weight = metric(type: MetricType.NUMERIC, baseline: 200d, target: 180d, direction: MetricDirectionType.DECREASE)

        MetricProgress result = progressOf(weight, values([50: 199d]) + values([50: 198d]) + values([50: 197d]) + values([50: 198d]) + values([50: 199d]))

        assert result.recentRate == null
        assert result.stalled == null
        assert result.requiredRate != null
    }

    @Test
    void testAScaleWithoutAnExplicitTargetHasNoRates() {
        GoalMetric mood = metric(type: MetricType.SCALE, scaleMin: 1d, scaleMax: 5d)

        MetricProgress result = progressOf(mood, values((40..50).collectEntries { [it, 3d] }))

        assert result.score != null
        assert result.requiredRate == null
        assert result.recentRate == null
        assert result.stalled == null
    }

    @Test
    void testAnUnrecordedTodayLeavesAFullWindowEndingYesterday() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        Map<Integer, Boolean> byDay = [22: false] + (23..49).collectEntries { [it, true] }

        MetricProgress result = progressOf(walked, answers(byDay))

        assert result.windowPeriods == 28
        assert near(result.adherence, 27 / 28d)
    }

    @Test
    void testAnAtLeastMetricScoresTheShareOfRecentValuesReachingTheTarget() {
        GoalMetric meals = metric(type: MetricType.NUMERIC, frequency: FrequencyType.WEEKLY, direction: MetricDirectionType.AT_LEAST, target: 4d)

        MetricProgress result = progressOf(meals, values([22: 5d, 29: 3d, 36: 4d, 43: 6d]))

        assert near(result.score, 0.75)
        assert near(result.expected, 1)
        assert near(result.current, 6)
        assert result.requiredRate == null
        assert result.stalled == null
    }

    @Test
    void testAnAtMostMetricScoresTheShareOfRecentValuesWithinTheTarget() {
        GoalMetric screenTime = metric(type: MetricType.NUMERIC, direction: MetricDirectionType.AT_MOST, target: 2d)

        MetricProgress result = progressOf(screenTime, values([47: 1.5d, 48: 3d, 49: 2d, 50: 2.5d]))

        assert near(result.score, 0.5)
    }

    private static int mondayOfWeek(int offset) {
        ChronoUnit.DAYS.between(START, START.plusDays(offset).with(DayOfWeek.MONDAY)) as int
    }

    @Test
    void testTimesPerWeekScoresDecidedWeeksAndLeavesUndecidedOnesOut() {
        GoalMetric running = metric(type: MetricType.BOOLEAN, timesPerWeek: 3d)
        int thisWeek = mondayOfWeek(50)
        int lastWeek = thisWeek - 7
        int twoWeeksAgo = thisWeek - 14
        int threeWeeksAgo = thisWeek - 21
        Map<Integer, Boolean> byDay = [(threeWeeksAgo): true, (threeWeeksAgo + 2): true] +
                [(twoWeeksAgo): true] + (1..5).collectEntries { [twoWeeksAgo + it, false] } +
                [(lastWeek): true, (lastWeek + 2): true, (lastWeek + 4): true] +
                [(thisWeek): true, (thisWeek + 1): true]

        MetricProgress result = progressOf(running, answers(byDay))

        assert result.windowPeriods == 3
        assert result.answeredPeriods == 2
        assert near(result.adherence, 0.5)
        assert near(result.score, 0.5)
        assert result.currentPeriodCount == 2
        assert result.currentStreak == 1
        assert result.bestStreak == 1
    }

    @Test
    void testAWeekIsMetAsSoonAsItReachesItsTimesAndMissedOnceItCannot() {
        GoalMetric running = metric(type: MetricType.BOOLEAN, timesPerWeek: 3d)
        int thisWeek = mondayOfWeek(50)

        MetricProgress met = progressOf(running, answers([(thisWeek): true, (thisWeek + 1): true, (thisWeek + 3): true]))
        MetricProgress missed = progressOf(running, answers((0..4).collectEntries { [thisWeek + it, false] }))

        assert met.windowPeriods == 1
        assert near(met.adherence, 1)
        assert met.currentStreak == 1
        assert missed.windowPeriods == 1
        assert near(missed.adherence, 0)
    }

    @Test
    void testTimesPerWeekLeavesOutYesDaysBeforeAMidweekGoalStart() {
        GoalMetric running = metric(type: MetricType.BOOLEAN, timesPerWeek: 3d)
        ProgressCalculator firstWeek = new ProgressCalculator(day(2))

        MetricProgress result = firstWeek.metricProgress(running, goal("g"), answers([(-3): true, (-2): true, 0: true]))

        assert result.currentPeriodCount == 1
        assert result.answeredPeriods == 0
        assert result.currentStreak == 0
    }

    @Test
    void testTheOwnersLocalDayDecidesWhatCountsAsToday() {
        GoalMetric walked = metric(type: MetricType.BOOLEAN)
        ProgressCalculator eveningInNewYork = new ProgressCalculator(NOW, START.plusDays(49))

        MetricProgress result = eveningInNewYork.metricProgress(walked, goal("g"), answers([48: true, 50: true]))

        assert result.answeredPeriods == 1
        assert result.windowPeriods == 1
        assert result.observationCount == 1
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
