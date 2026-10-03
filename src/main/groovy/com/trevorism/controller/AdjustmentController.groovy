package com.trevorism.controller

import com.trevorism.model.GoalAdjustment
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.AdjustmentService
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
class AdjustmentController {

    @Inject
    AdjustmentService adjustmentService

    @Tag(name = "Adjustment Operations")
    @Operation(summary = "List the adjustments of a goal **Secure")
    @Get(value = "/goal/{goalId}/adjustment", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<GoalAdjustment> listForGoal(String goalId, Authentication authentication) {
        adjustmentService.listForGoal(RequesterIdentity.of(authentication), goalId)
    }

    @Tag(name = "Adjustment Operations")
    @Operation(summary = "Record an adjustment on a goal **Secure")
    @Post(value = "/goal/{goalId}/adjustment", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalAdjustment create(String goalId, @Body GoalAdjustment adjustment, Authentication authentication) {
        adjustmentService.create(RequesterIdentity.of(authentication), goalId, adjustment)
    }

    @Tag(name = "Adjustment Operations")
    @Operation(summary = "Update an adjustment **Secure")
    @Put(value = "/adjustment/{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalAdjustment update(String id, @Body GoalAdjustment adjustment, Authentication authentication) {
        adjustmentService.update(RequesterIdentity.of(authentication), id, adjustment)
    }

    @Tag(name = "Adjustment Operations")
    @Operation(summary = "Delete an adjustment **Secure")
    @Delete(value = "/adjustment/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalAdjustment delete(String id, Authentication authentication) {
        adjustmentService.delete(RequesterIdentity.of(authentication), id)
    }
}
