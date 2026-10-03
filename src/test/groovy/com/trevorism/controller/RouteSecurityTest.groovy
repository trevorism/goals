package com.trevorism.controller

import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import org.junit.jupiter.api.Test

import java.lang.reflect.Method

class RouteSecurityTest {

    private static final List<Class> ROUTE_ANNOTATIONS = [Get, Post, Put, Patch, Delete]
    private static final List<Class> SECURED_CONTROLLERS = [GoalController, MetricController, ObservationController, AdjustmentController]

    @Test
    void testEveryGoalDataRouteRequiresAUser() {
        List<Method> routes = SECURED_CONTROLLERS.collectMany { Class controller ->
            controller.declaredMethods.findAll { Method method -> ROUTE_ANNOTATIONS.any { method.isAnnotationPresent(it) } }
        }

        assert routes.size() == 21
        routes.each { Method route ->
            Secure secure = route.getAnnotation(Secure)
            assert secure, "${route.declaringClass.simpleName}.${route.name} has no @Secure"
            assert secure.value() == Roles.USER
            assert !secure.allowInternal()
        }
    }

    @Test
    void testRequesterIdentityReadsTheIdClaim() {
        def authentication = io.micronaut.security.authentication.Authentication.build("someone", [id: "identity-7"])

        assert RequesterIdentity.of(authentication) == "identity-7"
        assert RequesterIdentity.of(null) == null
    }
}
