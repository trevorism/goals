package com.trevorism.controller

import com.trevorism.service.OwnerResolver
import io.micronaut.security.authentication.Authentication

class MeasurementAccess {

    static List<String> rolesFor(Authentication authentication) {
        if (authentication == null || OwnerResolver.currentDelegate() != null) {
            return []
        }
        (authentication.roles ?: []) as List<String>
    }
}
