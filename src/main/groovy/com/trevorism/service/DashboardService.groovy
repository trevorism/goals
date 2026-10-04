package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.dashboard.Dashboard
import com.trevorism.model.dashboard.DashboardGoal
import com.trevorism.model.dashboard.NeedsYouItem
import com.trevorism.model.progress.GoalProgress
import com.trevorism.model.progress.MetricProgress
import com.trevorism.model.progress.TreeProgress
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.NeedsYouType
import com.trevorism.model.types.PendingAskStatusType
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Singleton
class DashboardService {

    static final int MISSED_IN_A_ROW = 3

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalPendingAsk> pendingAskRepository
    private final ProgressService progressService
    private final ProfileService profileService

    DashboardService(@Named("goal") OwnedRepository<Goal> goalRepository,
                     @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                     @Named("pendingAsk") OwnedRepository<GoalPendingAsk> pendingAskRepository,
                     ProgressService progressService,
                     ProfileService profileService) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.pendingAskRepository = pendingAskRepository
        this.progressService = progressService
        this.profileService = profileService
    }

    Dashboard dashboard(String ownerId, String date) {
        LocalDate today = TodayService.parseDay(date)
        List<Goal> goals = goalRepository.list(ownerId)
        Map<String, Goal> goalsById = goals.collectEntries { [it.id, it] }
        Map<String, GoalMetric> metricsById = metricRepository.list(ownerId).collectEntries { [it.id, it] }
        List<DashboardGoal> rows = goals.findAll { it.parentId == null }
                .sort { a, b -> (a.status != GoalStatusType.ACTIVE) <=> (b.status != GoalStatusType.ACTIVE) ?: a.endDate <=> b.endDate ?: a.title <=> b.title }
                .collect { summarize(ownerId, it, metricsById) }
        List<NeedsYouItem> needsYou = unreadableAnswers(ownerId, goalsById, metricsById) +
                missedInARow(ownerId, goalsById, metricsById) +
                pastEndDate(goals, today)
        new Dashboard(asOf: new Date(), goals: rows, needsYou: needsYou)
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

    private List<NeedsYouItem> unreadableAnswers(String ownerId, Map<String, Goal> goalsById, Map<String, GoalMetric> metricsById) {
        ZoneId zone = profileService.zoneFor(ownerId)
        pendingAskRepository.listWhere(ownerId, "status", PendingAskStatusType.INVALID)
                .findAll { GoalPendingAsk ask ->
                    GoalMetric metric = metricsById[ask.metricId]
                    metric != null && isActive(goalsById[metric.goalId], goalsById) && !recordedSince(metric, periodDay(ask, zone))
                }
                .sort { it.periodStart }
                .collect { GoalPendingAsk ask ->
                    GoalMetric metric = metricsById[ask.metricId]
                    new NeedsYouItem(type: NeedsYouType.UNREADABLE_ANSWER, rootId: rootOf(goalsById[metric.goalId]), goalId: metric.goalId,
                            metricId: metric.id, title: metric.name, detail: ask.note, date: ask.periodStart)
                }
    }

    private List<NeedsYouItem> missedInARow(String ownerId, Map<String, Goal> goalsById, Map<String, GoalMetric> metricsById) {
        pendingAskRepository.list(ownerId)
                .findAll { it.status != PendingAskStatusType.OPEN }
                .groupBy { it.metricId }
                .collect { String metricId, List<GoalPendingAsk> asks ->
                    GoalMetric metric = metricsById[metricId]
                    if (metric == null || metric.source != MetricSourceType.PROMPT || metric.enabled == false || !isActive(goalsById[metric.goalId], goalsById)) {
                        return null
                    }
                    List<GoalPendingAsk> newestFirst = asks.sort { a, b -> b.periodStart <=> a.periodStart }
                    int missed = newestFirst.takeWhile { it.status == PendingAskStatusType.MISSED }.size()
                    missed >= MISSED_IN_A_ROW ? new NeedsYouItem(type: NeedsYouType.MISSED_QUESTIONS, rootId: rootOf(goalsById[metric.goalId]),
                            goalId: metric.goalId, metricId: metric.id, title: metric.name, date: newestFirst.first().periodStart, count: missed) : null
                }
                .findAll()
                .sort { a, b -> b.count <=> a.count }
    }

    private static List<NeedsYouItem> pastEndDate(List<Goal> goals, LocalDate today) {
        Map<String, Goal> goalsById = goals.collectEntries { [it.id, it] }
        goals.findAll { it.endDate != null && isActive(it, goalsById) && today.isAfter(utcDay(it.endDate)) }
                .sort { it.endDate }
                .collect { new NeedsYouItem(type: NeedsYouType.PAST_END_DATE, rootId: rootOf(it), goalId: it.id, title: it.title, date: it.endDate) }
    }

    private static boolean recordedSince(GoalMetric metric, LocalDate day) {
        metric.lastObservedAt != null && !utcDay(metric.lastObservedAt).isBefore(day)
    }

    private static LocalDate periodDay(GoalPendingAsk ask, ZoneId zone) {
        ask.periodStart.toInstant().atZone(zone).toLocalDate()
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
