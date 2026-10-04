package com.trevorism.service

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Validation {

    private static final Logger log = LoggerFactory.getLogger(Validation)

    static void require(boolean condition, String message) {
        if (!condition) {
            log.info("Rejected request: ${message}")
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, message)
        }
    }

    static void requireOneOf(String value, List<String> allowed, String field) {
        require(value in allowed, "${field} must be one of ${allowed}")
    }
}
