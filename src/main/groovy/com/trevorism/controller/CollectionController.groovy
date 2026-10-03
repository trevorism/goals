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
    @Operation(summary = "Daily tick: ask due prompt questions and close expired ones **Secure")
    @Post(value = "/tick", produces = MediaType.APPLICATION_JSON, consumes = MediaType.ALL)
    @Secure(value = Roles.SYSTEM, allowInternal = true)
    CollectionResult tick() {
        collectionService.tick()
    }

    @Tag(name = "Collection Operations")
    @Operation(summary = "Create the answer subscription and the daily tick schedule if they don't exist **Secure")
    @Post(value = "/provision", produces = MediaType.APPLICATION_JSON, consumes = MediaType.ALL)
    @Secure(Roles.SYSTEM)
    Map<String, String> provision() {
        provisioningService.provision()
    }
}
