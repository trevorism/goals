package com.trevorism.controller

import com.trevorism.model.dashboard.Dashboard
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.DashboardService
import com.trevorism.service.OwnerResolver
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/dashboard")
class DashboardController {

    @Inject
    OwnerResolver owners

    @Inject
    DashboardService dashboardService

    @Tag(name = "Dashboard Operations")
    @Operation(summary = "Status of every root goal and the few things that need the owner, for the given local date (yyyy-MM-dd) **Secure")
    @Get(value = "/{?date}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Dashboard dashboard(@Nullable @QueryValue String date, Authentication authentication) {
        dashboardService.dashboard(owners.ownerFor(authentication), date)
    }
}
