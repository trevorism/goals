package com.trevorism.controller

import com.trevorism.model.collection.CollectionResult
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.CollectionService
import com.trevorism.service.ProvisioningService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/collect")
class CollectionController {

    @Inject
    CollectionService collectionService

    @Inject
    ProvisioningService provisioningService

    @Tag(name = "Collection Operations")
    @Operation(summary = "Daily tick: record catalogued measurements for due measured metrics **Secure")
    @Post(value = "/tick", produces = MediaType.APPLICATION_JSON, consumes = MediaType.ALL)
    @Secure(value = Roles.SYSTEM, allowInternal = true)
    CollectionResult tick() {
        collectionService.tick()
    }

    @Tag(name = "Collection Operations")
    @Operation(summary = "Create the daily tick schedule if it doesn't exist **Secure")
    @Post(value = "/provision", produces = MediaType.APPLICATION_JSON, consumes = MediaType.ALL)
    @Secure(Roles.SYSTEM)
    Map<String, String> provision() {
        provisioningService.provision()
    }
}
