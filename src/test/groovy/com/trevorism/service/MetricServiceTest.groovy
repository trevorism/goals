package com.trevorism.service

import com.trevorism.model.Frequency
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricChoice
import com.trevorism.model.MetricSource
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
        assert metric.type == GoalMetric.NUMERIC
        assert metric.role == GoalMetric.OUTCOME
        assert metric.direction == GoalMetric.INCREASE
        assert metric.enabled
        assert metric.frequency.type == Frequency.DAILY
        assert metric.frequency.interval == 1
        assert metric.frequency.timezone == "UTC"
        assert metric.source.type == MetricSource.MANUAL
        assert metric.nextDueAt
    }

    @Test
    void testMetricOnAChildCarriesTheTreeRoot() {
        Goal child = store.goalService().createChild(OWNER, root.id, new Goal(title: "Sleep"))

        GoalMetric metric = service.create(OWNER, child.id, new GoalMetric(name: "Hours"))

        assert metric.goalId == child.id
        assert metric.rootId == root.id
    }

    @Test
    void testBooleanMetricsGetYesAndNoChoices() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Walked", type: GoalMetric.BOOLEAN, targetRate: 0.857))

        assert metric.choices*.label == ["Yes", "No"]
        assert metric.choices*.value == ["1", "0"]
        assert metric.direction == null
    }

    @Test
    void testChoiceValuesAreTheirIndexes() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Meal quality", type: GoalMetric.CHOICE,
                choices: [new MetricChoice(label: "poor", score: 0), new MetricChoice(label: "ok", score: 0.5), new MetricChoice(label: "good", score: 1)]))

        assert metric.choices*.value == ["0", "1", "2"]
    }

    @Test
    void testTypeSpecificValidation() {
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: "vibes")) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: GoalMetric.SCALE, scaleMin: 5, scaleMax: 1)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: GoalMetric.CHOICE, choices: [new MetricChoice(label: "only")])) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: GoalMetric.CHOICE, choices: [new MetricChoice(label: "a", score: 2), new MetricChoice(label: "b")])) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", type: GoalMetric.BOOLEAN, targetRate: 1.5)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", direction: GoalMetric.MAINTAIN)) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", frequency: new Frequency(type: "hourly"))) }
        assertBadRequest { service.create(OWNER, root.id, new GoalMetric(name: "x", source: new MetricSource(type: "telepathy"))) }
    }

    @Test
    void testUpdateMergesAndRejectsATypeChange() {
        GoalMetric metric = service.create(OWNER, root.id, new GoalMetric(name: "Weight", unit: "lb"))

        GoalMetric updated = service.update(OWNER, metric.id, new GoalMetric(target: 180, baseline: 200))

        assert updated.name == "Weight"
        assert updated.target == 180d
        assert updated.baseline == 200d
        assertBadRequest { service.update(OWNER, metric.id, new GoalMetric(type: GoalMetric.TEXT)) }
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
