package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.service.GoalServiceTest.assertNotFound
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class AdjustmentServiceTest {

    private final TestStore store = new TestStore()
    private final AdjustmentService service = store.adjustmentService()
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    @Test
    void testCreateAppliesDefaults() {
        GoalAdjustment adjustment = service.create(OWNER, root.id, new GoalAdjustment(title: "Started walking after lunch"))

        assert adjustment.goalId == root.id
        assert adjustment.category == "other"
        assert adjustment.metricIds == []
        assert adjustment.effectiveDate
    }

    @Test
    void testAdjustmentsMayTargetMetricsInTheSameTree() {
        Goal child = store.goalService().createChild(OWNER, root.id, new Goal(title: "Cardio"))
        GoalMetric weight = store.metricService().create(OWNER, root.id, new GoalMetric(name: "Weight"))

        GoalAdjustment adjustment = service.create(OWNER, child.id, new GoalAdjustment(title: "Walk daily", category: "habit", metricIds: [weight.id]))

        assert adjustment.metricIds == [weight.id]
    }

    @Test
    void testValidation() {
        Goal otherTree = store.goalService().createRoot(OWNER, rootGoal("Paying customers"))
        GoalMetric otherTreeMetric = store.metricService().create(OWNER, otherTree.id, new GoalMetric(name: "Customers"))

        assertBadRequest { service.create(OWNER, root.id, new GoalAdjustment(title: "")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalAdjustment(title: "x", category: "luck")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalAdjustment(title: "x", metricIds: [otherTreeMetric.id])) }
    }

    @Test
    void testUpdateAndListOrderedByEffectiveDate() {
        GoalAdjustment late = service.create(OWNER, root.id, new GoalAdjustment(title: "Caffeine before noon", effectiveDate: day(40)))
        service.create(OWNER, root.id, new GoalAdjustment(title: "Walk after lunch", effectiveDate: day(20)))

        service.update(OWNER, late.id, new GoalAdjustment(category: "habit"))

        assert service.listForGoal(OWNER, root.id)*.title == ["Walk after lunch", "Caffeine before noon"]
        assert store.adjustmentRepository.get(OWNER, late.id).category == "habit"
    }

    @Test
    void testAnotherOwnersGoalMetricsAndAdjustmentsAreNotFound() {
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal())
        GoalMetric theirMetric = store.metricService().create(OTHER_OWNER, theirs.id, new GoalMetric(name: "secret"))
        GoalAdjustment theirAdjustment = service.create(OTHER_OWNER, theirs.id, new GoalAdjustment(title: "theirs"))

        assertNotFound { service.create(OWNER, theirs.id, new GoalAdjustment(title: "x")) }
        assertNotFound { service.create(OWNER, root.id, new GoalAdjustment(title: "x", metricIds: [theirMetric.id])) }
        assertNotFound { service.listForGoal(OWNER, theirs.id) }
        assertNotFound { service.update(OWNER, theirAdjustment.id, new GoalAdjustment(title: "y")) }
        assertNotFound { service.delete(OWNER, theirAdjustment.id) }
    }
}
