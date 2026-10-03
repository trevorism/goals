package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric

class AdjustmentScope {

    static Map<String, List<GoalAdjustment>> byMetric(List<GoalMetric> metrics, List<GoalAdjustment> adjustments, List<Goal> treeGoals) {
        Map<String, Goal> goalsById = treeGoals.collectEntries { [it.id, it] }
        metrics.collectEntries { GoalMetric metric ->
            Set<String> lineage = lineageOf(metric.goalId, goalsById)
            [metric.id, adjustments.findAll { applies(it, metric, lineage) }]
        }
    }

    static boolean applies(GoalAdjustment adjustment, GoalMetric metric, Set<String> lineage) {
        adjustment.metricIds ? metric.id in adjustment.metricIds : adjustment.goalId in lineage
    }

    private static Set<String> lineageOf(String goalId, Map<String, Goal> goalsById) {
        Set<String> lineage = [goalId] as Set
        Goal goal = goalsById[goalId]
        while (goal?.parentId != null && lineage.add(goal.parentId)) {
            goal = goalsById[goal.parentId]
        }
        return lineage
    }
}
