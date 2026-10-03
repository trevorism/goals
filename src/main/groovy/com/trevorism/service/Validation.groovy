package com.trevorism.service

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException

class Validation {

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, message)
        }
    }

    static void requireOneOf(String value, List<String> allowed, String field) {
        require(value in allowed, "${field} must be one of ${allowed}")
    }
}
