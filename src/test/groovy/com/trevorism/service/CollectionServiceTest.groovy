package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalProfile
import com.trevorism.model.collection.CollectionResult
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.secure.Roles
import com.trevorism.support.FakeMeasurementReader
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class CollectionServiceTest {

    private static final Instant NOON_DAY_40 = day(40).toInstant().plusSeconds(12 * 3600)

    private final TestStore store = new TestStore()
    private final FakeMeasurementReader measurements = new FakeMeasurementReader()
    private final CollectionService service = new CollectionService(store.metrics, store.goals, store.observationService(), store.profileService(), measurements)
    private final Goal root = store.goalService().createRoot(OWNER, rootGoal())

    CollectionServiceTest() {
        at(NOON_DAY_40)
    }

    private void at(Instant instant) {
        service.clock = Clock.fixed(instant, ZoneOffset.UTC)
    }

    private GoalMetric measuredMetric(Map properties = [:], String owner = OWNER, Goal goal = root) {
        store.metricService().create(owner, goal.id, new GoalMetric([name: "Paying customers", source: MetricSourceType.HTTP,
                measurementId: MeasurementCatalog.BILLING_PAYING_CUSTOMERS] + properties), [Roles.ADMIN])
    }

    @Test
    void testTheTickRecordsOneMeasuredValuePerPeriod() {
        GoalMetric customers = measuredMetric(frequency: FrequencyType.WEEKLY)
        measurements.values[MeasurementCatalog.BILLING_PAYING_CUSTOMERS] = 2d

        CollectionResult first = service.tick()
        CollectionResult second = service.tick()

        assert first.measured == 1
        assert second.measured == 0
        assert second.alreadyRecorded == 1
        GoalObservation observation = store.observations.store.find { it.metricId == customers.id }
        assert observation.value == 2d
        assert observation.observedAt == day(39)
        assert observation.metricSource == MetricSourceType.HTTP
        assert observation.sourceRef == MeasurementCatalog.BILLING_PAYING_CUSTOMERS
        assert store.metricService().get(OWNER, customers.id).lastObservedAt == day(39)
    }

    @Test
    void testMetricsSharingAMeasurementReadItOncePerTick() {
        measuredMetric()
        measuredMetric([:], OTHER_OWNER, store.goalService().createRoot(OTHER_OWNER, rootGoal()))
        measurements.values[MeasurementCatalog.BILLING_PAYING_CUSTOMERS] = 1d

        CollectionResult result = service.tick()

        assert result.measured == 2
        assert measurements.reads == [MeasurementCatalog.BILLING_PAYING_CUSTOMERS]
    }

    @Test
    void testMeasuredMetricsOnClosedGoalsOrDisabledAreNotMeasured() {
        Goal later = store.goalService().createChild(OWNER, root.id, new Goal(title: "Later", startDate: day(50), endDate: day(60)))
        measuredMetric([:], OWNER, later)
        GoalMetric paused = measuredMetric()
        store.metricService().update(OWNER, paused.id, new GoalMetric(enabled: false))
        measurements.values[MeasurementCatalog.BILLING_PAYING_CUSTOMERS] = 1d

        CollectionResult result = service.tick()

        assert result.measured == 0
        assert measurements.reads.isEmpty()
    }

    @Test
    void testAFailedMeasurementIsCountedAndRetriedNextTick() {
        GoalMetric customers = measuredMetric()
        measurements.failing = true

        CollectionResult failed = service.tick()
        measurements.failing = false
        measurements.values[MeasurementCatalog.BILLING_PAYING_CUSTOMERS] = 3d
        CollectionResult retried = service.tick()

        assert failed.failed == 1
        assert retried.measured == 1
        assert store.observations.store.find { it.metricId == customers.id }.value == 3d
    }

    @Test
    void testAFailedMeasurementIsNotReadAgainForOtherMetricsInTheSameTick() {
        measuredMetric()
        measuredMetric([:], OTHER_OWNER, store.goalService().createRoot(OTHER_OWNER, rootGoal()))
        measurements.failing = true

        CollectionResult result = service.tick()

        assert result.failed == 2
        assert result.measured == 0
        assert measurements.reads == [MeasurementCatalog.BILLING_PAYING_CUSTOMERS]
    }

    @Test
    void testManualMetricsAreLeftForTheOwnerToRecord() {
        store.metricService().create(OWNER, root.id, new GoalMetric(name: "Weight"))

        CollectionResult result = service.tick()

        assert [result.measured, result.alreadyRecorded, result.failed] == [0, 0, 0]
        assert store.observations.store.isEmpty()
        assert measurements.reads.isEmpty()
    }

    @Test
    void testMeasuredPeriodsFollowTheOwnersTimezone() {
        store.profileService().update(OWNER, new GoalProfile(timezone: "America/New_York"))
        GoalMetric customers = measuredMetric()
        measurements.values[MeasurementCatalog.BILLING_PAYING_CUSTOMERS] = 1d
        at(day(40).toInstant().plusSeconds(2 * 3600))

        service.tick()

        assert store.observations.store.find { it.metricId == customers.id }.observedAt == day(39)
    }
}
