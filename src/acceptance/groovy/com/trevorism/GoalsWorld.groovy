package com.trevorism

import com.google.gson.Gson
import com.trevorism.http.HttpClient
import com.trevorism.http.JsonHttpClient
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient

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

    Map createRoot() {
        root = postJson("api/goal", [title: "${MARKER} root goal".toString(), startDate: "2026-10-01", endDate: "2027-03-31"])
        createdRootIds << (root.id as String)
        return root
    }

    Map createChildStep() {
        child = postJson("api/goal/${root.id}/child", [title: "${MARKER} step".toString(), definitionOfDone: "Done when acceptance passes"])
    }

    Map createBooleanMetricOnChild() {
        metric = postJson("api/goal/${child.id}/metric", [name: "${MARKER} did it".toString(), type: "boolean", targetRate: 0.8])
    }

    Map recordYes() {
        observation = postJson("api/metric/${metric.id}/observation", [choice: "yes"])
    }

    Map recordAdjustment() {
        adjustment = postJson("api/goal/${root.id}/adjustment", [title: "${MARKER} changed a habit".toString(), category: "habit", metricIds: [metric.id]])
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
