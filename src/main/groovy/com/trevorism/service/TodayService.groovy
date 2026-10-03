package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.today.TodayItem
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricSourceType
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

import static com.trevorism.service.Validation.require

@Singleton
class TodayService {

    static final List<String> ANSWERABLE_SOURCES = [MetricSourceType.MANUAL, MetricSourceType.PROMPT]

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository

    TodayService(@Named("goal") OwnedRepository<Goal> goalRepository,
                 @Named("metric") OwnedRepository<GoalMetric> metricRepository) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
    }

    List<TodayItem> due(String ownerId, String date) {
        LocalDate today = parseDay(date)
        Map<String, Goal> goalsById = goalRepository.list(ownerId).collectEntries { [it.id, it] }
        metricRepository.list(ownerId)
                .findAll { it.enabled != false && (it.source ?: MetricSourceType.MANUAL) in ANSWERABLE_SOURCES }
                .findAll { GoalMetric metric -> isOpen(goalsById[metric.goalId], goalsById, today) && isDue(metric, today) }
                .collect { GoalMetric metric -> toItem(metric, goalsById, today) }
                .sort { a, b -> a.rootTitle <=> b.rootTitle ?: a.goalTitle <=> b.goalTitle ?: a.metric.name <=> b.metric.name }
    }

    static boolean isDue(GoalMetric metric, LocalDate today) {
        if (metric.lastObservedAt == null) {
            return true
        }
        String frequency = metric.frequency ?: FrequencyType.DAILY
        ProgressCalculator.periodOf(toDay(metric.lastObservedAt), frequency).isBefore(ProgressCalculator.periodOf(today, frequency))
    }

    private static boolean isOpen(Goal goal, Map<String, Goal> goalsById, LocalDate today) {
        if (goal == null || today.isBefore(toDay(goal.startDate)) || today.isAfter(toDay(goal.endDate))) {
            return false
        }
        for (Goal current = goal; current != null; current = goalsById[current.parentId]) {
            if (current.status != GoalStatusType.ACTIVE) {
                return false
            }
        }
        return true
    }

    private static TodayItem toItem(GoalMetric metric, Map<String, Goal> goalsById, LocalDate today) {
        Goal goal = goalsById[metric.goalId]
        Goal root = goalsById[goal.treeRootId()] ?: goal
        LocalDate period = ProgressCalculator.periodOf(today, metric.frequency ?: FrequencyType.DAILY)
        new TodayItem(metric: metric, goalTitle: goal.title, rootId: root.id, rootTitle: root.title,
                periodStart: Date.from(period.atStartOfDay(ZoneOffset.UTC).toInstant()))
    }

    private static LocalDate parseDay(String date) {
        if (!date) {
            return LocalDate.now(ZoneOffset.UTC)
        }
        try {
            return LocalDate.parse(date)
        } catch (DateTimeParseException ignored) {
            require(false, "date must be formatted yyyy-MM-dd")
            return null
        }
    }

    private static LocalDate toDay(Date date) {
        date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
    }
}
