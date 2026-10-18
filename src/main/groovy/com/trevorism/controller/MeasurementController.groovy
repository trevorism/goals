package com.trevorism.controller

import com.trevorism.model.measurement.Measurement
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.MeasurementCatalog
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/measurement")
class MeasurementController {

    @Inject
    MeasurementCatalog measurementCatalog

    @Tag(name = "Measurement Operations")
    @Operation(summary = "List the automatic measurements you can attach to a metric **Secure")
    @Get(produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<Measurement> list(Authentication authentication) {
        measurementCatalog.availableTo(MeasurementAccess.rolesFor(authentication))
    }
}
