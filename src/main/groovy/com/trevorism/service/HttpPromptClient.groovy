package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class HttpPromptClient implements PromptClient {

    static final String BASE_URL = "https://prompt.action.trevorism.com"

    private final SecureHttpClient client

    HttpPromptClient(@Named("appClientSecureHttpClient") SecureHttpClient client) {
        this.client = client
    }

    @Override
    String askQuestion(Map question) {
        Map created = new JsonSlurper().parseText(client.post("${BASE_URL}/api/question".toString(), JsonOutput.toJson(question))) as Map
        created.id?.toString()
    }

    @Override
    Map getAnswer(String answerId) {
        new JsonSlurper().parseText(client.get("${BASE_URL}/api/answer/${answerId}".toString())) as Map
    }
}
