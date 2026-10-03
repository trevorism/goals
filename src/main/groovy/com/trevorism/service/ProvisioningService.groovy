package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.micronaut.context.annotation.Value
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.Clock
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Singleton
class ProvisioningService {

    static final String EVENT_BASE_URL = "https://event.data.trevorism.com"
    static final String SCHEDULE_BASE_URL = "https://schedule.action.trevorism.com"
    static final String SUBSCRIPTION_NAME = "goals-question-answered"
    static final String ANSWERED_TOPIC = "questionAnswered"
    static final String TICK_TASK_NAME = "goals_daily_tick"
    static final LocalTime TICK_TIME_UTC = LocalTime.of(11, 7)
    static final String CREATED = "created"
    static final String EXISTS = "exists"

    private final SecureHttpClient client
    private final String baseUrl
    Clock clock = Clock.systemUTC()

    ProvisioningService(@Named("appClientSecureHttpClient") SecureHttpClient client,
                        @Value('${goals.base-url:https://goals.action.trevorism.com}') String baseUrl) {
        this.client = client
        this.baseUrl = baseUrl
    }

    Map<String, String> provision() {
        [subscription: ensureSubscription(), schedule: ensureTickSchedule()]
    }

    String ensureSubscription() {
        List subscriptions = new JsonSlurper().parseText(client.get("${EVENT_BASE_URL}/subscription".toString())) as List
        if (subscriptions.any { it.name == SUBSCRIPTION_NAME }) {
            return EXISTS
        }
        client.post("${EVENT_BASE_URL}/subscription".toString(), JsonOutput.toJson([
                name : SUBSCRIPTION_NAME,
                topic: ANSWERED_TOPIC,
                url  : "${baseUrl}/api/event/questionAnswered".toString()
        ]))
        return CREATED
    }

    String ensureTickSchedule() {
        List tasks = new JsonSlurper().parseText(client.get("${SCHEDULE_BASE_URL}/api/schedule".toString())) as List
        if (tasks.any { it.name == TICK_TASK_NAME }) {
            return EXISTS
        }
        client.post("${SCHEDULE_BASE_URL}/api/schedule".toString(), JsonOutput.toJson([
                name       : TICK_TASK_NAME,
                type       : "daily",
                startDate  : nextTickTime(),
                enabled    : true,
                endpoint   : "${baseUrl}/api/collect/tick".toString(),
                httpMethod : "post",
                requestJson: "{}"
        ]))
        return CREATED
    }

    String nextTickTime() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
        ZonedDateTime next = now.with(TICK_TIME_UTC).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(next)
    }
}
