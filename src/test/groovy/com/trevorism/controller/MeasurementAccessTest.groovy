package com.trevorism.controller

import com.trevorism.secure.Roles
import com.trevorism.service.OwnerResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.security.authentication.Authentication
import org.junit.jupiter.api.Test

class MeasurementAccessTest {

    private final Authentication admin = Authentication.build("admin", [Roles.ADMIN], [id: "admin-1"])

    @Test
    void testTheOwnerActingDirectlyKeepsTheirRoles() {
        HttpRequest<?> request = HttpRequest.GET("/api/measurement")

        Collection<String> roles = ServerRequestContext.with(request, { MeasurementAccess.rolesFor(admin) } as java.util.function.Supplier)

        assert roles == [Roles.ADMIN]
    }

    @Test
    void testADelegateGetsNoMeasurementRoles() {
        HttpRequest<?> request = HttpRequest.GET("/api/measurement")
        request.setAttribute(OwnerResolver.DELEGATE_ATTRIBUTE, "admin-1")

        Collection<String> roles = ServerRequestContext.with(request, { MeasurementAccess.rolesFor(admin) } as java.util.function.Supplier)

        assert roles == []
    }

    @Test
    void testNoAuthenticationMeansNoRoles() {
        assert MeasurementAccess.rolesFor(null) == []
    }
}
