package com.trevorism.controller

import com.trevorism.model.Goal
import com.trevorism.model.GoalTreeNode
import com.trevorism.model.progress.TreeProgress
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.GoalService
import com.trevorism.service.ProgressService
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

@Controller("/api/goal")
class GoalController {

    @Inject
    GoalService goalService

    @Inject
    ProgressService progressService

    @Tag(name = "Goal Operations")
    @Operation(summary = "List the root goals of the current user **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    List<Goal> listRoots(Authentication authentication) {
        goalService.listRoots(RequesterIdentity.of(authentication))
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "Create a root goal **Secure")
    @Post(value = "/", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Goal createRoot(@Body Goal goal, Authentication authentication) {
        goalService.createRoot(RequesterIdentity.of(authentication), goal)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "View a goal by id **Secure")
    @Get(value = "{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Goal get(String id, Authentication authentication) {
        goalService.get(RequesterIdentity.of(authentication), id)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "Update a goal **Secure")
    @Put(value = "{id}", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Goal update(String id, @Body Goal goal, Authentication authentication) {
        goalService.update(RequesterIdentity.of(authentication), id, goal)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "Delete a goal with its subtree, metrics, observations and adjustments **Secure")
    @Delete(value = "{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Goal delete(String id, Authentication authentication) {
        goalService.delete(RequesterIdentity.of(authentication), id)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "Add a child goal **Secure")
    @Post(value = "{id}/child", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    Goal createChild(String id, @Body Goal goal, Authentication authentication) {
        goalService.createChild(RequesterIdentity.of(authentication), id, goal)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "View the subtree of a goal with its metrics **Secure")
    @Get(value = "{id}/tree", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    GoalTreeNode tree(String id, Authentication authentication) {
        goalService.tree(RequesterIdentity.of(authentication), id)
    }

    @Tag(name = "Goal Operations")
    @Operation(summary = "Progress, status and trend fits for a goal's subtree and its metrics **Secure")
    @Get(value = "{id}/progress", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.USER)
    TreeProgress progress(String id, Authentication authentication) {
        progressService.progress(RequesterIdentity.of(authentication), id)
    }
}
