package com.trevorism.model.types

class MetricSourceType {

    static final String MANUAL = "manual"
    static final String HTTP = "http"
    static final String EVENT = "event"
    static final String AGGREGATION = "aggregation"
    static final List<String> ALL = [MANUAL, HTTP, EVENT, AGGREGATION]
}
