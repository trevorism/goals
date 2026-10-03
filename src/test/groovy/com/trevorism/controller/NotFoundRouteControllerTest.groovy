package com.trevorism.controller

import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import org.junit.jupiter.api.Test

class NotFoundRouteControllerTest {

    @Test
    void testForwardServesIndexHtmlWhenHtmlAccepted() {
        URL resource = getClass().getResource("/logback.xml")
        NotFoundRouteController controller = new NotFoundRouteController(
                resourceResolver: [getResource: { String path -> Optional.of(resource) }] as ResourceResolver)

        HttpRequest request = HttpRequest.GET("/missing/route").accept(MediaType.TEXT_HTML)

        HttpResponse response = controller.forward(request)

        assert response.status() == HttpStatus.OK
        assert response.getBody().get() instanceof StreamedFile
    }

    @Test
    void testForwardReturnsNotFoundWhenHtmlNotAccepted() {
        NotFoundRouteController controller = new NotFoundRouteController(
                resourceResolver: [getResource: { String path -> Optional.empty() }] as ResourceResolver)

        HttpRequest request = HttpRequest.GET("/missing/route").accept(MediaType.APPLICATION_JSON)

        HttpResponse response = controller.forward(request)

        assert response.status() == HttpStatus.NOT_FOUND
    }

    @Test
    void testForwardReturnsNotFoundForAssetPath() {
        NotFoundRouteController controller = new NotFoundRouteController(
                resourceResolver: [getResource: { String path -> Optional.empty() }] as ResourceResolver)

        HttpRequest request = HttpRequest.GET("/assets/missing.js").accept(MediaType.TEXT_HTML)

        HttpResponse response = controller.forward(request)

        assert response.status() == HttpStatus.NOT_FOUND
    }

    @Test
    void testForwardReturnsNotFoundForApiPath() {
        NotFoundRouteController controller = new NotFoundRouteController(
                resourceResolver: [getResource: { String path -> Optional.empty() }] as ResourceResolver)

        HttpRequest request = HttpRequest.GET("/api/missing").accept(MediaType.TEXT_HTML)

        HttpResponse response = controller.forward(request)

        assert response.status() == HttpStatus.NOT_FOUND
    }

    @Test
    void testForwardReturnsNotFoundForPathWithFileExtension() {
        NotFoundRouteController controller = new NotFoundRouteController(
                resourceResolver: [getResource: { String path -> Optional.empty() }] as ResourceResolver)

        HttpRequest request = HttpRequest.GET("/favicon.ico").accept(MediaType.TEXT_HTML)

        HttpResponse response = controller.forward(request)

        assert response.status() == HttpStatus.NOT_FOUND
    }
}
