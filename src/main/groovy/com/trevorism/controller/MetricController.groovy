package com.trevorism.controller

import com.trevorism.model.GoalMetric
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.MetricService
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
class MetricController {

    @Inject
    MetricService metricService

    @Tag(name = "Metric Operations")
    @Operation(summary = "List the metrics of a goal **Secure")
    @Get(value = "/goal/{goalId}/metric", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<GoalMetric> listForGoal(String goalId, Authentication authentication) {
        metricService.listForGoal(RequesterIdentity.of(authentication), goalId)
    }

    @Tag(name = "Metric Operations")
    @Operation(summary = "Create a metric on a goal **Secure")
    @Post(value = "/goal/{goalId}/metric", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalMetric create(String goalId, @Body GoalMetric metric, Authentication authentication) {
        metricService.create(RequesterIdentity.of(authentication), goalId, metric)
    }

    @Tag(name = "Metric Operations")
    @Operation(summary = "View a metric by id **Secure")
    @Get(value = "/metric/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalMetric get(String id, Authentication authentication) {
        metricService.get(RequesterIdentity.of(authentication), id)
    }

    @Tag(name = "Metric Operations")
    @Operation(summary = "Update a metric **Secure")
    @Put(value = "/metric/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalMetric update(String id, @Body GoalMetric metric, Authentication authentication) {
        metricService.update(RequesterIdentity.of(authentication), id, metric)
    }

    @Tag(name = "Metric Operations")
    @Operation(summary = "Delete a metric and its observations **Secure")
    @Delete(value = "/metric/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalMetric delete(String id, Authentication authentication) {
        metricService.delete(RequesterIdentity.of(authentication), id)
    }
}
