package com.trevorism.model

import org.junit.jupiter.api.Test

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class DatastoreEntityTest {

    private static final List<Class> ENTITIES = [Goal, GoalMetric, GoalObservation, GoalAdjustment]
    private static final List<Class> FLAT_TYPES = [String, Date, Double, Boolean]

    @Test
    void testStoredEntitiesAvoidTheKeyColumnTheDatastoreRejects() {
        (ENTITIES + Choice).each { Class entity ->
            assert !entity.metaClass.properties*.name.contains("key"), "${entity.simpleName} has a 'key' property"
        }
    }

    @Test
    void testKindsDoNotCollideWithGenericNamesInTheSharedNamespace() {
        assert ENTITIES*.simpleName*.toLowerCase().every { it.startsWith("goal") }
    }

    @Test
    void testEntitiesAreFlatApartFromStringListsAndChoices() {
        ENTITIES.each { Class entity ->
            storedFields(entity).each { Field field ->
                boolean flat = field.type in FLAT_TYPES
                boolean list = List.isAssignableFrom(field.type)
                assert flat || list, "${entity.simpleName}.${field.name} is a nested ${field.type.simpleName}"
            }
        }
        assert storedFields(GoalMetric).find { it.name == "choices" }.genericType.typeName.contains("Choice")
        assert storedFields(GoalAdjustment).find { it.name == "metricIds" }.genericType.typeName.contains("String")
    }

    private static List<Field> storedFields(Class entity) {
        entity.declaredFields.findAll { !Modifier.isStatic(it.modifiers) && !it.synthetic && !it.name.startsWith('$') && it.name != "metaClass" } as List<Field>
    }
}
