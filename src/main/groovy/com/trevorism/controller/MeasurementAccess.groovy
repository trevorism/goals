package com.trevorism.controller

import com.trevorism.service.OwnerResolver
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.security.authentication.Authentication

class MeasurementAccess {

    static List<String> rolesFor(Authentication authentication) {
        if (authentication == null || OwnerResolver.currentDelegate() != null || actsOnBehalfOfAnother(authentication)) {
            return []
        }
        (authentication.roles ?: []) as List<String>
    }

    private static boolean actsOnBehalfOfAnother(Authentication authentication) {
        String onBehalfOf = ServerRequestContext.currentRequest()
                .map { it.parameters.get(OwnerResolver.ON_BEHALF_OF) }
                .orElse(null)
        onBehalfOf && onBehalfOf != RequesterIdentity.of(authentication)
    }
}
