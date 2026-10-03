package com.trevorism.service

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

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ProgressCalculator {

    static final double ON_TRACK_BAND = 0.05
    static final double AT_RISK_PROJECTION = 0.9
    static final int MINIMUM_FIT_POINTS = 3
    static final int ROLLING_OBSERVATIONS = 7
    static final int DAILY_ADHERENCE_DAYS = 28
    static final int ADHERENCE_PERIODS = 4
    static final double DAY_MILLIS = 86_400_000d
    static final double PROJECTION_HORIZON_MULTIPLE = 2d

    private final Date now
    private final LocalDate today

    ProgressCalculator(Date now) {
        this.now = now
        this.today = toLocalDate(now)
    }

    TreeProgress calculate(Goal top, List<Goal> subtree, List<GoalMetric> metrics, Map<String, List<GoalObservation>> observationsByMetric) {
        Map<String, Goal> goalsById = subtree.collectEntries { [it.id, it] }
        Map<String, List<Goal>> childrenByParent = subtree.groupBy { it.parentId }
        Map<String, List<GoalMetric>> metricsByGoal = metrics.groupBy { it.goalId }

        List<MetricProgress> metricResults = metrics.findAll { goalsById.containsKey(it.goalId) }.collect { GoalMetric metric ->
            metricProgress(metric, goalsById[metric.goalId], observationsByMetric[metric.id] ?: [])
        }
        Map<String, MetricProgress> metricResultsById = metricResults.collectEntries { [it.metricId, it] }

        Map<String, GoalProgress> goalResults = [:]
        goalProgress(top, childrenByParent, metricsByGoal, metricResultsById, goalResults)
        new TreeProgress(asOf: now, goals: goalResults.values().toList(), metrics: metricResults)
    }

    private GoalProgress goalProgress(Goal goal, Map<String, List<Goal>> childrenByParent, Map<String, List<GoalMetric>> metricsByGoal,
                                      Map<String, MetricProgress> metricResults, Map<String, GoalProgress> results) {
        List<GoalMetric> ownMetrics = metricsByGoal[goal.id] ?: []
        List<MetricProgress> outcome = scored(ownMetrics, MetricMeasuresType.OUTCOME, metricResults)
        List<MetricProgress> effort = scored(ownMetrics, MetricMeasuresType.EFFORT, metricResults)

        List<Contribution> outcomeItems = outcome.collect { new Contribution(it.score, it.score - it.expected, it.status) }
        List<Contribution> effortItems = effort.collect { new Contribution(it.score, it.score - it.expected, it.status) }
        (childrenByParent[goal.id] ?: []).each { Goal child ->
            GoalProgress childResult = goalProgress(child, childrenByParent, metricsByGoal, metricResults, results)
            if (child.status != GoalStatusType.ABANDONED) {
                double childProgress = childResult.progress ?: 0d
                double childPace = childResult.pace != null ? childResult.pace : childProgress - childResult.expected
                effortItems << new Contribution(childProgress, childPace, childResult.status)
            }
        }

        double expected = elapsedFraction(goal)
        List<Contribution> headline = outcomeItems ?: effortItems
        Double progress = mean(headline*.progress)
        Double pace = mean(headline*.pace)
        if (goal.status == GoalStatusType.COMPLETED) {
            progress = 1d
            pace = 1d - expected
        }
        String status = pace == null ? ProgressStatusType.NO_DATA : statusOf(pace)
        if (status in [ProgressStatusType.AHEAD, ProgressStatusType.ON_TRACK] && goal.status != GoalStatusType.COMPLETED && headline.any { it.status == ProgressStatusType.AT_RISK }) {
            status = ProgressStatusType.AT_RISK
        }

        GoalProgress result = new GoalProgress(goalId: goal.id, parentId: goal.parentId, progress: progress,
                outcomeProgress: mean(outcomeItems*.progress), effortProgress: mean(effortItems*.progress),
                expected: expected, pace: pace, status: status)
        results[goal.id] = result
        return result
    }

    private static List<MetricProgress> scored(List<GoalMetric> metrics, String measures, Map<String, MetricProgress> metricResults) {
        metrics.findAll { it.measures == measures }.collect { metricResults[it.id] }.findAll { it?.score != null }
    }

    MetricProgress metricProgress(GoalMetric metric, Goal goal, List<GoalObservation> allObservations) {
        List<GoalObservation> observations = allObservations.findAll { it.observedAt != null && !it.observedAt.after(now) }
                .sort { a, b -> a.observedAt <=> b.observedAt ?: a.createdDate <=> b.createdDate }
        MetricProgress result = new MetricProgress(metricId: metric.id, goalId: metric.goalId,
                observationCount: observations.count { !it.missed } as Integer)
        switch (metric.type) {
            case MetricType.NUMERIC:
            case MetricType.SCALE:
                measureTrend(metric, goal, observations.findAll { !it.missed && it.value != null }, result)
                break
            case MetricType.BOOLEAN:
                measureAdherence(metric, goal, observations, result)
                break
            case MetricType.CHOICE:
                measureChoices(metric, observations.findAll { !it.missed && it.value != null }, result)
                break
        }
        boolean steady = metric.type in [MetricType.BOOLEAN, MetricType.CHOICE] || metric.direction == MetricDirectionType.MAINTAIN
        result.expected = steady ? 1d : elapsedFraction(goal)
        result.status = result.score == null ? ProgressStatusType.NO_DATA : statusOf(result.score - result.expected)
        if (result.status in [ProgressStatusType.AHEAD, ProgressStatusType.ON_TRACK] && result.projectedProgress != null && result.projectedProgress < AT_RISK_PROJECTION) {
            result.status = ProgressStatusType.AT_RISK
        }
        return result
    }

    private void measureTrend(GoalMetric metric, Goal goal, List<GoalObservation> observations, MetricProgress result) {
        if (!observations) {
            return
        }
        List<double[]> points = observations.collect { [daysFromStart(goal, it.observedAt), it.value] as double[] }
        LinearFit fit = points.size() >= MINIMUM_FIT_POINTS ? LinearFit.of(points) : null
        if (fit) {
            result.fitSlope = fit.slope
            result.fitIntercept = fit.intercept
            result.fitR2 = fit.r2
            result.fitCount = fit.count
        }

        if (metric.direction == MetricDirectionType.MAINTAIN) {
            List<GoalObservation> recent = observations.takeRight(ROLLING_OBSERVATIONS)
            result.current = recent.last().value
            if (metric.target != null && metric.tolerance != null) {
                result.score = recent.count { Math.abs(it.value - metric.target) <= metric.tolerance } / (double) recent.size()
            }
            return
        }

        boolean scale = metric.type == MetricType.SCALE
        result.current = scale ? mean(observations.takeRight(ROLLING_OBSERVATIONS)*.value) : (fit ? fit.valueAt(points.last()[0]) : points.last()[1])
        Double baseline = metric.baseline != null ? metric.baseline : (scale ? scaleStart(metric) : points.first()[1])
        Double target = metric.target != null ? metric.target : (scale ? scaleEnd(metric) : null)
        if (target == null) {
            return
        }
        result.score = fractionToward(result.current, baseline, target, metric.direction)
        if (fit) {
            double endX = daysFromStart(goal, goal.endDate)
            result.projectedEnd = scale ? Math.max(metric.scaleMin, Math.min(metric.scaleMax, fit.valueAt(endX))) : fit.valueAt(endX)
            result.projectedProgress = fractionToward(result.projectedEnd, baseline, target, metric.direction)
            result.projectedTargetDate = projectedTargetDate(goal, fit, result.current, target, metric.direction, endX)
        }
    }

    private Date projectedTargetDate(Goal goal, LinearFit fit, double current, double target, String direction, double endX) {
        if (reached(current, target, direction) || fit.slope == 0d) {
            return null
        }
        double x = (target - fit.intercept) / fit.slope
        double nowX = daysFromStart(goal, now)
        double horizon = nowX + PROJECTION_HORIZON_MULTIPLE * Math.max(endX - nowX, 0d)
        if (x < nowX || x > horizon) {
            return null
        }
        new Date((goal.startDate.time + x * DAY_MILLIS) as long)
    }

    private static boolean reached(double value, double target, String direction) {
        direction == MetricDirectionType.DECREASE ? value <= target : value >= target
    }

    private static double fractionToward(double value, double baseline, double target, String direction) {
        if (target == baseline) {
            return reached(value, target, direction) ? 1d : 0d
        }
        clamp((value - baseline) / (target - baseline))
    }

    private static Double scaleStart(GoalMetric metric) {
        metric.direction == MetricDirectionType.DECREASE ? metric.scaleMax : metric.scaleMin
    }

    private static Double scaleEnd(GoalMetric metric) {
        metric.direction == MetricDirectionType.DECREASE ? metric.scaleMin : metric.scaleMax
    }

    private void measureChoices(GoalMetric metric, List<GoalObservation> observations, MetricProgress result) {
        int choiceCount = metric.choices?.size() ?: 0
        if (!observations || choiceCount < 2) {
            return
        }
        List<Double> positions = observations.takeRight(ROLLING_OBSERVATIONS).collect { it.value / (choiceCount - 1) }
        result.current = observations.last().value
        result.score = clamp(mean(positions))
    }

    private void measureAdherence(GoalMetric metric, Goal goal, List<GoalObservation> observations, MetricProgress result) {
        if (!observations) {
            return
        }
        String frequency = metric.frequency ?: FrequencyType.DAILY
        Map<LocalDate, Boolean> answers = [:]
        observations.each { answers[periodOf(toLocalDate(it.observedAt), frequency)] = !it.missed && it.value == 1d }

        LocalDate current = periodOf(today, frequency)
        LocalDate firstAnswered = answers.keySet().min()
        LocalDate windowStart = current.minus(windowPeriods(frequency) - 1, unitOf(frequency))
        LocalDate trackingStart = [windowStart, firstAnswered, periodOf(toLocalDate(goal.startDate), frequency)].max()

        List<LocalDate> window = periodsBetween(trackingStart, current, frequency).findAll { it != current || answers.containsKey(current) }
        if (window) {
            result.adherence = window.count { answers[it] } / (double) window.size()
            result.score = metric.target != null ? Math.min(result.adherence / metric.target, 1d) : result.adherence
        }
        result.current = result.adherence

        List<LocalDate> history = periodsBetween(firstAnswered, current, frequency)
        int best = 0
        int run = 0
        history.each { LocalDate period ->
            run = answers[period] ? run + 1 : 0
            best = Math.max(best, run)
        }
        List<LocalDate> streakPeriods = answers.containsKey(current) ? history : history.dropRight(1)
        result.currentStreak = streakPeriods.reverse().takeWhile { answers[it] }.size()
        result.bestStreak = best
    }

    private static int windowPeriods(String frequency) {
        frequency == FrequencyType.DAILY ? DAILY_ADHERENCE_DAYS : ADHERENCE_PERIODS
    }

    private static ChronoUnit unitOf(String frequency) {
        switch (frequency) {
            case FrequencyType.WEEKLY: return ChronoUnit.WEEKS
            case FrequencyType.MONTHLY: return ChronoUnit.MONTHS
            default: return ChronoUnit.DAYS
        }
    }

    static LocalDate periodOf(LocalDate date, String frequency) {
        switch (frequency) {
            case FrequencyType.WEEKLY: return date.with(DayOfWeek.MONDAY)
            case FrequencyType.MONTHLY: return date.withDayOfMonth(1)
            default: return date
        }
    }

    private static List<LocalDate> periodsBetween(LocalDate from, LocalDate to, String frequency) {
        List<LocalDate> periods = []
        for (LocalDate period = from; !period.isAfter(to); period = period.plus(1, unitOf(frequency))) {
            periods << period
        }
        return periods
    }

    double elapsedFraction(Goal goal) {
        double span = goal.endDate.time - goal.startDate.time
        span <= 0 ? 1d : clamp((now.time - goal.startDate.time) / span)
    }

    private static double daysFromStart(Goal goal, Date date) {
        (date.time - goal.startDate.time) / DAY_MILLIS
    }

    private static String statusOf(double pace) {
        if (pace > ON_TRACK_BAND) {
            return ProgressStatusType.AHEAD
        }
        pace >= -ON_TRACK_BAND ? ProgressStatusType.ON_TRACK : ProgressStatusType.BEHIND
    }

    private static LocalDate toLocalDate(Date date) {
        date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
    }

    private static Double mean(List<Double> values) {
        values ? values.sum() / values.size() : null
    }

    private static double clamp(double value) {
        Math.max(0d, Math.min(1d, value))
    }

    private static class Contribution {
        final double progress
        final double pace
        final String status

        Contribution(double progress, double pace, String status) {
            this.progress = progress
            this.pace = pace
            this.status = status
        }
    }
}
