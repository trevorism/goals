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
    private static final List<Class> USER_CONTROLLERS = [GoalController, MetricController, ObservationController, AdjustmentController, TodayController, ProfileController]

    private static List<Method> routesOf(Class controller) {
        controller.declaredMethods.findAll { Method method -> ROUTE_ANNOTATIONS.any { method.isAnnotationPresent(it) } }
    }

    @Test
    void testEveryUserDataRouteRequiresAUserAndRejectsInternalTokens() {
        List<Method> routes = USER_CONTROLLERS.collectMany { routesOf(it) }

        assert routes.size() == 24
        routes.each { Method route ->
            Secure secure = route.getAnnotation(Secure)
            assert secure, "${route.declaringClass.simpleName}.${route.name} has no @Secure"
            assert secure.value() == Roles.USER
            assert !secure.allowInternal()
        }
    }

    @Test
    void testCollectionRoutesRequireTheSystemRole() {
        Map<String, Secure> secured = routesOf(CollectionController).collectEntries { [it.name, it.getAnnotation(Secure)] }

        assert secured.keySet() == ["tick", "provision"] as Set
        assert secured.tick.value() == Roles.SYSTEM && secured.tick.allowInternal()
        assert secured.provision.value() == Roles.SYSTEM && !secured.provision.allowInternal()
    }

    @Test
    void testTheEventReceiverAcceptsTheForwardedPublisherToken() {
        List<Method> routes = routesOf(EventReceiverController)

        assert routes*.name == ["questionAnswered"]
        Secure secure = routes[0].getAnnotation(Secure)
        assert secure.value() == Roles.USER
        assert secure.allowInternal()
    }

    @Test
    void testRequesterIdentityReadsTheIdClaim() {
        def authentication = io.micronaut.security.authentication.Authentication.build("someone", [id: "identity-7"])

        assert RequesterIdentity.of(authentication) == "identity-7"
        assert RequesterIdentity.of(null) == null
    }
}
