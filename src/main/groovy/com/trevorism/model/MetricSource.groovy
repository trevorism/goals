package com.trevorism.model

class MetricSource {

    static final String MANUAL = "manual"
    static final String PROMPT = "prompt"
    static final String HTTP = "http"
    static final String EVENT = "event"
    static final String AGGREGATION = "aggregation"
    static final List<String> ALL = [MANUAL, PROMPT, HTTP, EVENT, AGGREGATION]
}
