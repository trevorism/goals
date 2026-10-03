package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.progress.TreeProgress
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.Clock

@Singleton
class ProgressService {

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalObservation> observationRepository
    private final OwnedRepository<GoalAdjustment> adjustmentRepository
    Clock clock = Clock.systemUTC()

    ProgressService(@Named("goal") OwnedRepository<Goal> goalRepository,
                    @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                    @Named("observation") OwnedRepository<GoalObservation> observationRepository,
                    @Named("adjustment") OwnedRepository<GoalAdjustment> adjustmentRepository) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.observationRepository = observationRepository
        this.adjustmentRepository = adjustmentRepository
    }

    TreeProgress progress(String ownerId, String id) {
        Goal top = goalRepository.get(ownerId, id)
        String rootId = top.treeRootId()
        List<Goal> treeGoals = goalRepository.listWhere(ownerId, "rootId", rootId)
        List<Goal> subtree = [top] + descendants(top, treeGoals)
        Set<String> subtreeIds = subtree*.id as Set
        List<GoalMetric> metrics = metricRepository.listWhere(ownerId, "rootId", rootId).findAll { it.goalId in subtreeIds }
        Map<String, List<GoalObservation>> observations = metrics.collectEntries { GoalMetric metric ->
            [metric.id, observationRepository.listWhere(ownerId, "metricId", metric.id)]
        }
        Goal root = top.parentId ? goalRepository.get(ownerId, rootId) : top
        List<Goal> wholeTree = [root] + treeGoals
        Set<String> treeIds = wholeTree*.id as Set
        List<GoalAdjustment> adjustments = adjustmentRepository.list(ownerId).findAll { it.goalId in treeIds }
        Map<String, List<GoalAdjustment>> adjustmentsByMetric = AdjustmentScope.byMetric(metrics, adjustments, wholeTree)
        new ProgressCalculator(Date.from(clock.instant())).calculate(top, subtree, metrics, observations, adjustmentsByMetric)
    }

    private static List<Goal> descendants(Goal top, List<Goal> treeGoals) {
        Map<String, List<Goal>> childrenByParent = treeGoals.groupBy { it.parentId }
        List<Goal> result = []
        List<Goal> frontier = childrenByParent[top.id] ?: []
        while (frontier) {
            result.addAll(frontier)
            frontier = frontier.collectMany { childrenByParent[it.id] ?: [] }
        }
        return result
    }
}
