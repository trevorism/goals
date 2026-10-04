package com.trevorism.controller

import com.trevorism.model.GoalDelegate
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.DelegateService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/delegate")
class DelegateController {

    @Inject
    DelegateService delegateService

    @Tag(name = "Delegate Operations")
    @Operation(summary = "List who may act on the current user's goals **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<GoalDelegate> list(Authentication authentication) {
        delegateService.list(RequesterIdentity.of(authentication))
    }

    @Tag(name = "Delegate Operations")
    @Operation(summary = "Let another identity act on the current user's goals (access read or edit; delegates can never delete) **Secure")
    @Post(value = "/", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalDelegate grant(@Body GoalDelegate delegate, Authentication authentication) {
        delegateService.grant(RequesterIdentity.of(authentication), delegate)
    }

    @Tag(name = "Delegate Operations")
    @Operation(summary = "Revoke a delegate's access **Secure")
    @Delete(value = "{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalDelegate revoke(String id, Authentication authentication) {
        delegateService.revoke(RequesterIdentity.of(authentication), id)
    }

    @Tag(name = "Delegate Operations")
    @Operation(summary = "List the owners whose goals the current user may act on; pass ?onBehalfOf=<ownerId> on other routes to act for them **Secure")
    @Get(value = "/granted", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<GoalDelegate> granted(Authentication authentication) {
        delegateService.grantedTo(RequesterIdentity.of(authentication))
    }
}
