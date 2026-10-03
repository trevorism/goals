package com.trevorism.controller

import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.AnswerIntakeService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/api/event")
class EventReceiverController {

    @Inject
    AnswerIntakeService answerIntakeService

    @Tag(name = "Event Operations")
    @Operation(summary = "Receives prompt's questionAnswered events and records answers to goal questions after checking them with prompt **Secure")
    @Post(value = "/questionAnswered", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, allowInternal = true)
    Map<String, String> questionAnswered(@Body Map event) {
        [outcome: answerIntakeService.questionAnswered(event)]
    }
}
