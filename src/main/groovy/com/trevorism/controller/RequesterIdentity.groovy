package com.trevorism.controller

import io.micronaut.security.authentication.Authentication

class RequesterIdentity {

    static String of(Authentication authentication) {
        authentication?.getAttributes()?.get("id") as String
    }
}
