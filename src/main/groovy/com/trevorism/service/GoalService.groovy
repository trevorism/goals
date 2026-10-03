package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalTreeNode
import com.trevorism.model.types.GoalStatusType
import jakarta.inject.Named
import jakarta.inject.Singleton

import static com.trevorism.service.Validation.require
import static com.trevorism.service.Validation.requireOneOf

@Singleton
class GoalService {

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalObservation> observationRepository
    private final OwnedRepository<GoalAdjustment> adjustmentRepository

    GoalService(@Named("goal") OwnedRepository<Goal> goalRepository,
                @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                @Named("observation") OwnedRepository<GoalObservation> observationRepository,
                @Named("adjustment") OwnedRepository<GoalAdjustment> adjustmentRepository) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.observationRepository = observationRepository
        this.adjustmentRepository = adjustmentRepository
    }

    List<Goal> listRoots(String ownerId) {
        goalRepository.list(ownerId).findAll { it.parentId == null }.sort { it.createdDate }
    }

    Goal get(String ownerId, String id) {
        goalRepository.get(ownerId, id)
    }

    Goal createRoot(String ownerId, Goal goal) {
        goal.parentId = null
        goal.rootId = null
        applyCreateDefaults(goal)
        require(goal.startDate != null && goal.endDate != null, "startDate and endDate are required")
        validate(goal, null)
        goalRepository.create(ownerId, goal)
    }

    Goal createChild(String ownerId, String parentId, Goal goal) {
        Goal parent = goalRepository.get(ownerId, parentId)
        goal.parentId = parent.id
        goal.rootId = parent.treeRootId()
        goal.startDate = goal.startDate ?: parent.startDate
        goal.endDate = goal.endDate ?: parent.endDate
        applyCreateDefaults(goal)
        validate(goal, parent)
        goalRepository.create(ownerId, goal)
    }

    Goal update(String ownerId, String id, Goal changes) {
        Goal existing = goalRepository.get(ownerId, id)
        String previousStatus = existing.status
        existing.title = changes.title ?: existing.title
        existing.description = changes.description ?: existing.description
        existing.status = changes.status ?: existing.status
        existing.startDate = changes.startDate ?: existing.startDate
        existing.endDate = changes.endDate ?: existing.endDate
        existing.definitionOfDone = changes.definitionOfDone ?: existing.definitionOfDone
        if (existing.status != GoalStatusType.ACTIVE && previousStatus == GoalStatusType.ACTIVE) {
            existing.completedDate = new Date()
        }
        Goal parent = existing.parentId ? goalRepository.get(ownerId, existing.parentId) : null
        validate(existing, parent)
        goalRepository.update(ownerId, id, existing)
    }

    Goal delete(String ownerId, String id) {
        Goal goal = goalRepository.get(ownerId, id)
        List<Goal> subtree = [goal] + descendants(ownerId, goal)
        subtree.reverse().each { deleteNodeAndData(ownerId, it) }
        return goal
    }

    GoalTreeNode tree(String ownerId, String id) {
        Goal goal = goalRepository.get(ownerId, id)
        String rootId = goal.treeRootId()
        List<Goal> treeGoals = goalRepository.listWhere(ownerId, "rootId", rootId)
        List<GoalMetric> treeMetrics = metricRepository.listWhere(ownerId, "rootId", rootId)
        Map<String, List<Goal>> childrenByParent = treeGoals.groupBy { it.parentId }
        Map<String, List<GoalMetric>> metricsByGoal = treeMetrics.groupBy { it.goalId }
        buildNode(goal, childrenByParent, metricsByGoal)
    }

    private List<Goal> descendants(String ownerId, Goal goal) {
        List<Goal> treeGoals = goalRepository.listWhere(ownerId, "rootId", goal.treeRootId())
        Map<String, List<Goal>> childrenByParent = treeGoals.groupBy { it.parentId }
        List<Goal> result = []
        List<Goal> frontier = childrenByParent[goal.id] ?: []
        while (frontier) {
            result.addAll(frontier)
            frontier = frontier.collectMany { childrenByParent[it.id] ?: [] }
        }
        return result
    }

    private void deleteNodeAndData(String ownerId, Goal goal) {
        metricRepository.listWhere(ownerId, "goalId", goal.id).each { GoalMetric metric ->
            observationRepository.listWhere(ownerId, "metricId", metric.id).each { observationRepository.delete(ownerId, it.id) }
            metricRepository.delete(ownerId, metric.id)
        }
        adjustmentRepository.listWhere(ownerId, "goalId", goal.id).each { adjustmentRepository.delete(ownerId, it.id) }
        goalRepository.delete(ownerId, goal.id)
    }

    private static GoalTreeNode buildNode(Goal goal, Map<String, List<Goal>> childrenByParent, Map<String, List<GoalMetric>> metricsByGoal) {
        List<Goal> children = (childrenByParent[goal.id] ?: []).sort { a, b -> a.startDate <=> b.startDate ?: a.createdDate <=> b.createdDate }
        new GoalTreeNode(
                goal: goal,
                metrics: metricsByGoal[goal.id] ?: [],
                children: children.collect { buildNode(it, childrenByParent, metricsByGoal) })
    }

    private static void applyCreateDefaults(Goal goal) {
        goal.status = goal.status ?: GoalStatusType.ACTIVE
        goal.createdDate = new Date()
        goal.completedDate = goal.status != GoalStatusType.ACTIVE ? new Date() : null
    }

    private static void validate(Goal goal, Goal parent) {
        require(goal.title?.trim() as boolean, "title is required")
        requireOneOf(goal.status, GoalStatusType.ALL, "status")
        require(goal.endDate.after(goal.startDate), "endDate must be after startDate")
        if (parent) {
            require(!goal.startDate.before(parent.startDate) && !goal.endDate.after(parent.endDate),
                    "dates must fall within the parent goal's dates")
        }
    }
}
