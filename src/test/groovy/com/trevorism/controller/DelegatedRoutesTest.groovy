package com.trevorism.controller

import com.trevorism.service.OwnerResolver
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.security.authentication.Authentication
import org.junit.jupiter.api.Test

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class DelegatedRoutesTest {

    private static final List<Class> DELEGABLE = [GoalController, MetricController, ObservationController, AdjustmentController, TodayController, DashboardController]
    private static final List<Class> ROUTES = [Get, Post, Put, Delete]

    private static class RecordingResolver extends OwnerResolver {
        final List<Authentication> calls = []

        RecordingResolver() {
            super(null)
        }

        @Override
        String ownerFor(Authentication authentication) {
            calls << authentication
            return "resolved-owner"
        }
    }

    private static Object argumentFor(Class type, Authentication authentication) {
        if (type == Authentication) return authentication
        if (type == String) return "1"
        if (type == Map) return [:]
        type.getDeclaredConstructor().newInstance()
    }

    @Test
    void everyDelegableRouteGetsItsOwnerFromTheResolver() {
        int checked = 0
        DELEGABLE.each { Class controllerType ->
            controllerType.declaredMethods.findAll { Method method -> ROUTES.any { method.isAnnotationPresent(it) } }.each { Method route ->
                Object controller = controllerType.getDeclaredConstructor().newInstance()
                RecordingResolver resolver = new RecordingResolver()
                controller.owners = resolver
                Authentication authentication = Authentication.build("caller", [id: "caller"])
                try {
                    route.invoke(controller, route.parameterTypes.collect { argumentFor(it, authentication) } as Object[])
                } catch (InvocationTargetException ignored) {
                }
                assert resolver.calls == [authentication], "${controllerType.simpleName}.${route.name} doesn't resolve its owner through OwnerResolver"
                checked++
            }
        }
        assert checked == 23
    }
}
