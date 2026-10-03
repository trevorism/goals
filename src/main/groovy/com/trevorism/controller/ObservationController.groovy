package com.trevorism.controller

import com.trevorism.model.GoalObservation
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.ObservationService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api")
class ObservationController {

    @Inject
    ObservationService observationService

    @Tag(name = "Observation Operations")
    @Operation(summary = "List the observations of a metric **Secure")
    @Get(value = "/metric/{metricId}/observation", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<GoalObservation> listForMetric(String metricId, Authentication authentication) {
        observationService.listForMetric(RequesterIdentity.of(authentication), metricId)
    }

    @Tag(name = "Observation Operations")
    @Operation(summary = "Record an observation **Secure")
    @Post(value = "/metric/{metricId}/observation", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalObservation create(String metricId, @Body GoalObservation observation, Authentication authentication) {
        observationService.create(RequesterIdentity.of(authentication), metricId, observation)
    }

    @Tag(name = "Observation Operations")
    @Operation(summary = "Update an observation **Secure")
    @Put(value = "/observation/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalObservation update(String id, @Body GoalObservation observation, Authentication authentication) {
        observationService.update(RequesterIdentity.of(authentication), id, observation)
    }

    @Tag(name = "Observation Operations")
    @Operation(summary = "Delete an observation **Secure")
    @Delete(value = "/observation/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalObservation delete(String id, Authentication authentication) {
        observationService.delete(RequesterIdentity.of(authentication), id)
    }
}
