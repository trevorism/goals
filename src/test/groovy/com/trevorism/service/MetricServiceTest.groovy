package com.trevorism.service

import com.trevorism.model.Choice
import com.trevorism.model.Frequency
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricDirection
import com.trevorism.model.MetricRole
import com.trevorism.model.MetricSource
import com.trevorism.model.MetricType
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.service.GoalServiceTest.assertNotFound
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.rootGoal

class MetricServiceTest {

    private final TestStore store = new TestStore()
    private final MetricService service = store.metricService()
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    @Test
    void testCreateAppliesDefaults() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Body weight", unit: "lb"))

        assert metric.goalId == root.id
        assert metric.rootId == root.id
        assert metric.type == MetricType.NUMERIC
        assert metric.role == MetricRole.OUTCOME
        assert metric.direction == MetricDirection.INCREASE
        assert metric.enabled
        assert metric.frequency == Frequency.DAILY
        assert metric.source == MetricSource.MANUAL
        assert metric.choices == []
        assert metric.nextDueAt
    }

    @Test
    void testMetricOnAChildCarriesTheTreeRoot() {
        Goal child = store.goalService().createChild(OWNER, root.id, new Goal(title: "Sleep"))

        GoalMetric metric = service.create(OWNER, child.id, new GoalMetric(name: "Hours", frequency: Frequency.WEEKLY))

        assert metric.goalId == child.id
        assert metric.rootId == root.id
        assert metric.frequency == Frequency.WEEKLY
    }

    @Test
    void testBooleanMetricsGetYesAndNoChoices() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Walked", type: MetricType.BOOLEAN, target: 0.857,
                choices: [new Choice(label: "Maybe")]))

        assert metric.choices*.value == ["yes", "no"]
        assert metric.choices*.label == ["Yes", "No"]
        assert metric.direction == null
        assert metric.target == 0.857d
    }

    @Test
    void testChoiceValuesFollowThePromptConvention() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Meal quality", type: MetricType.CHOICE,
                choices: [new Choice(label: " Poor "), new Choice(label: "Just OK"), new Choice(label: "just ok"), new Choice(value: "great", label: "Good")]))

        assert metric.choices*.value == ["poor", "just-ok", "just-ok-2", "great"]
        assert metric.choices*.label == ["Poor", "Just OK", "just ok", "Good"]
    }

    @Test
    void testTypeSpecificValidation() {
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: "vibes")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", role: "hope")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", direction: "sideways")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: MetricType.SCALE, scaleMin: 5, scaleMax: 1)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: MetricType.CHOICE, choices: [new Choice(label: "only")])) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: MetricType.CHOICE, choices: [new Choice(label: "a"), new Choice(label: " ")])) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: MetricType.BOOLEAN, target: 1.5)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", direction: MetricDirection.MAINTAIN)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", frequency: "hourly")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", source: "telepathy")) }
    }

    @Test
    void testUpdateMergesAndRejectsATypeChange() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Weight", unit: "lb"))

        GoalMetric updated = service.update(OWNER, metric.id, new GoalMetric(target: 180, baseline: 200, frequency: Frequency.MONTHLY))

        assert updated.name == "Weight"
        assert updated.target == 180d
        assert updated.baseline == 200d
        assert updated.frequency == Frequency.MONTHLY
        assertBadRequest { service.update(OWNER, metric.id, new GoalMetric(type: MetricType.TEXT)) }
    }

    @Test
    void testDeleteRemovesItsObservations() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Weight"))
        GoalMetric other = service.create(OWNER, root.id, new GoalMetric(name: "Energy"))
        store.observationService().create(OWNER, metric.id, new GoalObservation(value: 200))
        store.observationService().create(OWNER, other.id, new GoalObservation(value: 3))

        service.delete(OWNER, metric.id)

        assert store.metrics.store*.name == ["Energy"]
        assert store.observations.store*.value == [3d]
    }

    @Test
    void testAnotherOwnersGoalAndMetricsAreNotFound() {
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal())
        GoalMetric theirMetric = service.create(OTHER_OWNER, theirs.id, new GoalMetric(name: "secret"))

        assertNotFound { service.create(OWNER, theirs.id, new GoalMetric(name: "x")) }
        assertNotFound { service.listForGoal(OWNER, theirs.id) }
        assertNotFound { service.get(OWNER, theirMetric.id) }
        assertNotFound { service.update(OWNER, theirMetric.id, new GoalMetric(name: "y")) }
        assertNotFound { service.delete(OWNER, theirMetric.id) }
    }

    @Test
    void testListForGoalReturnsThatGoalsMetrics() {
        Goal child = store.goalService().createChild(OWNER, root.id, new Goal(title: "Sleep"))
        service.create(OWNER, root.id, new GoalMetric(name: "Weight"))
        service.create(OWNER, child.id, new GoalMetric(name: "Hours"))

        assert service.listForGoal(OWNER, child.id)*.name == ["Hours"]
    }
}
