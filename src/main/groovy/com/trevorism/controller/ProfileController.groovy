package com.trevorism.controller

import com.trevorism.model.GoalProfile
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.ProfileService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/profile")
class ProfileController {

    @Inject
    ProfileService profileService

    @Tag(name = "Profile Operations")
    @Operation(summary = "View the current user's goals profile **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalProfile get(Authentication authentication) {
        profileService.get(RequesterIdentity.of(authentication))
    }

    @Tag(name = "Profile Operations")
    @Operation(summary = "Update the current user's goals profile (timezone) **Secure")
    @Put(value = "/", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalProfile update(@Body GoalProfile profile, Authentication authentication) {
        profileService.update(RequesterIdentity.of(authentication), profile)
    }
}
