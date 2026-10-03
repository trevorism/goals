package com.trevorism

import com.google.gson.Gson
import com.trevorism.http.HttpClient
import com.trevorism.http.JsonHttpClient
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient

import java.time.LocalDate
import java.time.ZoneOffset

class GoalsWorld {

    static final String BASE_URL = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://goals.action.trevorism.com"
    static final String MARKER = "[acceptance]"

    private final Gson gson = new Gson()
    private final SecureHttpClient authClient = new AppClientSecureHttpClient()
    private final HttpClient anonClient = new JsonHttpClient()

    final List<String> createdRootIds = []

    boolean rejected
    Map root
    Map child
    Map metric
    Map observation
    Map adjustment
    Map tree
    Map progress
    Map numericMetric
    List todayItems
    Map profile
    Map eventOutcome

    Map createRoot() {
        root = postJson("api/goal", [title: "${MARKER} root goal".toString(), startDate: daysFromToday(-2), endDate: daysFromToday(180)])
        createdRootIds << (root.id as String)
        return root
    }

    Map createChildStep() {
        child = postJson("api/goal/${root.id}/child", [title: "${MARKER} step".toString(), definitionOfDone: "Done when acceptance passes"])
    }

    Map createBooleanMetricOnChild() {
        metric = postJson("api/goal/${child.id}/metric", [name: "${MARKER} did it".toString(), type: "boolean", target: 0.8])
    }

    Map recordYes() {
        observation = postJson("api/metric/${metric.id}/observation", [choice: "yes"])
    }

    Map recordAdjustment() {
        adjustment = postJson("api/goal/${root.id}/adjustment", [title: "${MARKER} changed a habit".toString(), category: "habit", metricIds: [metric.id]])
    }

    Map createNumericMetricOnRoot() {
        numericMetric = postJson("api/goal/${root.id}/metric", [name: "${MARKER} weight".toString(), unit: "lb", frequency: "daily"])
    }

    Map recordNumericValue() {
        postJson("api/metric/${numericMetric.id}/observation", [value: 180, observedAt: today()])
    }

    List fetchToday() {
        todayItems = gson.fromJson(authClient.get("${BASE_URL}/api/today?date=${today()}".toString()), List)
    }

    static String today() {
        daysFromToday(0)
    }

    static String daysFromToday(int days) {
        LocalDate.now(ZoneOffset.UTC).plusDays(days).toString()
    }

    Map fetchProfile() {
        profile = getJson("api/profile")
    }

    Map updateProfile(String timezone) {
        profile = gson.fromJson(authClient.put("${BASE_URL}/api/profile".toString(), gson.toJson([timezone: timezone])), Map)
    }

    Map sendAnsweredEvent(Map event) {
        eventOutcome = postJson("api/event/questionAnswered", event)
    }

    void attemptAuthenticatedPut(String path, Map body) {
        try {
            authClient.put("${BASE_URL}/${path}".toString(), gson.toJson(body))
            rejected = false
        } catch (Exception ignored) {
            rejected = true
        }
    }

    Map fetchProgress() {
        progress = getJson("api/goal/${root.id}/progress")
    }

    Map fetchTree() {
        tree = getJson("api/goal/${root.id}/tree")
    }

    List fetchRoots() {
        gson.fromJson(authClient.get("${BASE_URL}/api/goal".toString()), List)
    }

    List fetchObservations() {
        gson.fromJson(authClient.get("${BASE_URL}/api/metric/${metric.id}/observation".toString()), List)
    }

    void deleteRoot() {
        authClient.delete("${BASE_URL}/api/goal/${root.id}".toString())
        createdRootIds.remove(root.id as String)
    }

    void attemptAuthenticatedGet(String path) {
        try {
            authClient.get("${BASE_URL}/${path}".toString())
            rejected = false
        } catch (Exception ignored) {
            rejected = true
        }
    }

    void attemptAuthenticatedPost(String path, Map body) {
        try {
            authClient.post("${BASE_URL}/${path}".toString(), gson.toJson(body))
            rejected = false
        } catch (Exception ignored) {
            rejected = true
        }
    }

    void anonGet(String path) {
        try {
            anonClient.get("${BASE_URL}/${path}".toString())
            rejected = false
        } catch (Exception ignored) {
            rejected = true
        }
    }

    void anonPost(String path, Map body) {
        try {
            anonClient.post("${BASE_URL}/${path}".toString(), gson.toJson(body))
            rejected = false
        } catch (Exception ignored) {
            rejected = true
        }
    }

    void cleanup() {
        createdRootIds.each { String id -> try { authClient.delete("${BASE_URL}/api/goal/${id}".toString()) } catch (ignored) {} }
        createdRootIds.clear()
    }

    private Map postJson(String path, Map body) {
        gson.fromJson(authClient.post("${BASE_URL}/${path}".toString(), gson.toJson(body)), Map)
    }

    private Map getJson(String path) {
        gson.fromJson(authClient.get("${BASE_URL}/${path}".toString()), Map)
    }
}
