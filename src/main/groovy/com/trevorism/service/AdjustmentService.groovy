package com.trevorism.service

import com.trevorism.model.AdjustmentCategory
import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import jakarta.inject.Named
import jakarta.inject.Singleton

import static com.trevorism.service.Validation.require
import static com.trevorism.service.Validation.requireOneOf

@Singleton
class AdjustmentService {

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalAdjustment> adjustmentRepository

    AdjustmentService(@Named("goal") OwnedRepository<Goal> goalRepository,
                      @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                      @Named("adjustment") OwnedRepository<GoalAdjustment> adjustmentRepository) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.adjustmentRepository = adjustmentRepository
    }

    List<GoalAdjustment> listForGoal(String ownerId, String goalId) {
        goalRepository.get(ownerId, goalId)
        adjustmentRepository.listWhere(ownerId, "goalId", goalId).sort { it.effectiveDate }
    }

    GoalAdjustment create(String ownerId, String goalId, GoalAdjustment adjustment) {
        Goal goal = goalRepository.get(ownerId, goalId)
        adjustment.goalId = goal.id
        adjustment.metricIds = adjustment.metricIds ?: []
        adjustment.effectiveDate = adjustment.effectiveDate ?: new Date()
        adjustment.category = adjustment.category ?: AdjustmentCategory.OTHER
        adjustment.createdDate = new Date()
        validate(ownerId, goal, adjustment)
        adjustmentRepository.create(ownerId, adjustment)
    }

    GoalAdjustment update(String ownerId, String id, GoalAdjustment changes) {
        GoalAdjustment existing = adjustmentRepository.get(ownerId, id)
        Goal goal = goalRepository.get(ownerId, existing.goalId)
        existing.title = changes.title ?: existing.title
        existing.description = changes.description ?: existing.description
        existing.category = changes.category ?: existing.category
        existing.effectiveDate = changes.effectiveDate ?: existing.effectiveDate
        existing.metricIds = changes.metricIds != null ? changes.metricIds : existing.metricIds
        validate(ownerId, goal, existing)
        adjustmentRepository.update(ownerId, id, existing)
    }

    GoalAdjustment delete(String ownerId, String id) {
        adjustmentRepository.delete(ownerId, id)
    }

    private void validate(String ownerId, Goal goal, GoalAdjustment adjustment) {
        require(adjustment.title?.trim() as boolean, "title is required")
        requireOneOf(adjustment.category, AdjustmentCategory.ALL, "category")
        adjustment.metricIds.each { String metricId ->
            GoalMetric metric = metricRepository.get(ownerId, metricId)
            require(metric.rootId == goal.treeRootId(), "metricIds must belong to the same goal tree")
        }
    }
}
