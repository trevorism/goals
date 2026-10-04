package com.trevorism.controller

import com.trevorism.model.today.TodayItem
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.OwnerResolver
import com.trevorism.service.TodayService
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/today")
class TodayController {

    @Inject
    OwnerResolver owners

    @Inject
    TodayService todayService

    @Tag(name = "Today Operations")
    @Operation(summary = "Metrics with nothing recorded yet in the current period, for the given local date (yyyy-MM-dd) **Secure")
    @Get(value = "/{?date}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<TodayItem> due(@Nullable @QueryValue String date, Authentication authentication) {
        todayService.due(owners.ownerFor(authentication), date)
    }
}
