package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricChoice
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.service.GoalServiceTest.assertNotFound
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class ObservationServiceTest {

    private final TestStore store = new TestStore()
    private final ObservationService service = store.observationService()
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    private GoalMetric metric(Map properties) {
        store.metricService().create(OWNER, root.id, new GoalMetric([name: "m"] + properties))
    }

    @Test
    void testNumericObservationDefaultsAndMarksTheMetricCollected() {
        GoalMetric weight = metric(type: GoalMetric.NUMERIC)

        GoalObservation observation = service.create(OWNER, weight.id, new GoalObservation(value: 201.5))

        assert observation.metricId == weight.id
        assert observation.ownerId == OWNER
        assert observation.observedAt
        assert observation.source == "manual"
        assert !observation.missed
        assert store.metricRepository.get(OWNER, weight.id).lastCollectedAt
        assertBadRequest { service.create(OWNER, weight.id, new GoalObservation()) }
    }

    @Test
    void testBooleanObservationsAreOneOrZeroAndLabelled() {
        GoalMetric walked = metric(type: GoalMetric.BOOLEAN)

        assert service.create(OWNER, walked.id, new GoalObservation(value: 1)).label == "Yes"
        assert service.create(OWNER, walked.id, new GoalObservation(value: 0)).label == "No"
        assertBadRequest { service.create(OWNER, walked.id, new GoalObservation(value: 2)) }
    }

    @Test
    void testScaleObservationsMustBeInRange() {
        GoalMetric energy = metric(type: GoalMetric.SCALE, scaleMin: 1, scaleMax: 5)

        assert service.create(OWNER, energy.id, new GoalObservation(value: 4)).value == 4d
        assertBadRequest { service.create(OWNER, energy.id, new GoalObservation(value: 6)) }
        assertBadRequest { service.create(OWNER, energy.id, new GoalObservation(value: 0)) }
    }

    @Test
    void testChoiceObservationsTakeTheChoiceLabel() {
        GoalMetric meal = metric(type: GoalMetric.CHOICE, choices: [new MetricChoice(label: "poor"), new MetricChoice(label: "good")])

        assert service.create(OWNER, meal.id, new GoalObservation(value: 1)).label == "good"
        assertBadRequest { service.create(OWNER, meal.id, new GoalObservation(value: 2)) }
        assertBadRequest { service.create(OWNER, meal.id, new GoalObservation(value: 0.5)) }
    }

    @Test
    void testTextObservationsRequireALabelAndHaveNoValue() {
        GoalMetric journal = metric(type: GoalMetric.TEXT)

        GoalObservation entry = service.create(OWNER, journal.id, new GoalObservation(label: "Slept badly", value: 3))

        assert entry.value == null
        assertBadRequest { service.create(OWNER, journal.id, new GoalObservation(label: " ")) }
    }

    @Test
    void testMissedObservationsCarryNoValue() {
        GoalMetric walked = metric(type: GoalMetric.BOOLEAN)

        GoalObservation missed = service.create(OWNER, walked.id, new GoalObservation(missed: true, value: 1))

        assert missed.missed
        assert missed.value == null
        assert missed.label == null
    }

    @Test
    void testListIsOrderedByObservedDate() {
        GoalMetric weight = metric(type: GoalMetric.NUMERIC)
        service.create(OWNER, weight.id, new GoalObservation(value: 199, observedAt: day(20)))
        service.create(OWNER, weight.id, new GoalObservation(value: 201, observedAt: day(10)))

        assert service.listForMetric(OWNER, weight.id)*.value == [201d, 199d]
    }

    @Test
    void testUpdateRevalidatesAgainstTheMetric() {
        GoalMetric energy = metric(type: GoalMetric.SCALE, scaleMin: 1, scaleMax: 5)
        GoalObservation observation = service.create(OWNER, energy.id, new GoalObservation(value: 3))

        assert service.update(OWNER, observation.id, new GoalObservation(value: 5, note: "great day")).note == "great day"
        assertBadRequest { service.update(OWNER, observation.id, new GoalObservation(value: 9)) }
    }

    @Test
    void testAnotherOwnersMetricAndObservationsAreNotFound() {
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal())
        GoalMetric theirMetric = store.metricService().create(OTHER_OWNER, theirs.id, new GoalMetric(name: "secret"))
        GoalObservation theirObservation = service.create(OTHER_OWNER, theirMetric.id, new GoalObservation(value: 1))

        assertNotFound { service.create(OWNER, theirMetric.id, new GoalObservation(value: 1)) }
        assertNotFound { service.listForMetric(OWNER, theirMetric.id) }
        assertNotFound { service.update(OWNER, theirObservation.id, new GoalObservation(value: 2)) }
        assertNotFound { service.delete(OWNER, theirObservation.id) }
    }
}
