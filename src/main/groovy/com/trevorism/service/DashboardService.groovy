package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.dashboard.Dashboard
import com.trevorism.model.dashboard.DashboardGoal
import com.trevorism.model.dashboard.NeedsYouItem
import com.trevorism.model.progress.GoalProgress
import com.trevorism.model.progress.MetricProgress
import com.trevorism.model.progress.TreeProgress
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.NeedsYouType
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.LocalDate
import java.time.ZoneOffset

@Singleton
class DashboardService {

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final ProgressService progressService

    DashboardService(@Named("goal") OwnedRepository<Goal> goalRepository,
                     @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                     ProgressService progressService) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.progressService = progressService
    }

    Dashboard dashboard(String ownerId, String date) {
        LocalDate today = TodayService.parseDay(date)
        List<Goal> goals = goalRepository.list(ownerId)
        Map<String, GoalMetric> metricsById = metricRepository.list(ownerId).collectEntries { [it.id, it] }
        List<DashboardGoal> rows = goals.findAll { it.parentId == null }
                .sort { a, b -> (a.status != GoalStatusType.ACTIVE) <=> (b.status != GoalStatusType.ACTIVE) ?: a.endDate <=> b.endDate ?: a.title <=> b.title }
                .collect { summarize(ownerId, it, metricsById) }
        new Dashboard(asOf: new Date(), goals: rows, needsYou: pastEndDate(goals, today))
    }

    private DashboardGoal summarize(String ownerId, Goal root, Map<String, GoalMetric> metricsById) {
        TreeProgress tree = progressService.progress(ownerId, root.id)
        GoalProgress progress = tree.goals.find { it.goalId == root.id }
        MetricProgress headline = tree.metrics
                .findAll { it.goalId == root.id && it.score != null && metricsById[it.metricId]?.measures == MetricMeasuresType.OUTCOME }
                .min { it.score - it.expected }
        new DashboardGoal(goal: root, progress: progress, headlineMetric: headline ? metricsById[headline.metricId] : null,
                headlineMetricProgress: headline)
    }

    private static List<NeedsYouItem> pastEndDate(List<Goal> goals, LocalDate today) {
        Map<String, Goal> goalsById = goals.collectEntries { [it.id, it] }
        goals.findAll { it.endDate != null && isActive(it, goalsById) && today.isAfter(utcDay(it.endDate)) }
                .sort { it.endDate }
                .collect { new NeedsYouItem(type: NeedsYouType.PAST_END_DATE, rootId: rootOf(it), goalId: it.id, title: it.title, date: it.endDate) }
    }

    private static boolean isActive(Goal goal, Map<String, Goal> goalsById) {
        if (goal == null) {
            return false
        }
        for (Goal current = goal; current != null; current = goalsById[current.parentId]) {
            if (current.status != GoalStatusType.ACTIVE) {
                return false
            }
        }
        return true
    }

    private static String rootOf(Goal goal) {
        goal?.treeRootId()
    }

    private static LocalDate utcDay(Date date) {
        date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
    }
}
