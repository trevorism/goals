package com.trevorism.model

import org.junit.jupiter.api.Test

class DatastoreEntityTest {

    private static final List<Class> STORED = [Goal, Automation, GoalMetric, MetricChoice, Frequency, MetricSource, GoalObservation, GoalAdjustment]

    @Test
    void testStoredEntitiesAvoidTheKeyColumnTheDatastoreRejects() {
        STORED.each { Class entity ->
            assert !entity.metaClass.properties*.name.contains("key"), "${entity.simpleName} has a 'key' property"
        }
    }

    @Test
    void testKindsDoNotCollideWithGenericNamesInTheSharedNamespace() {
        assert [Goal, GoalMetric, GoalObservation, GoalAdjustment]*.simpleName*.toLowerCase().every { it.startsWith("goal") }
    }
}
