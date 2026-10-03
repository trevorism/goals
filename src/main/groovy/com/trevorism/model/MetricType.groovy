package com.trevorism.model

class MetricType {

    static final String NUMERIC = "numeric"
    static final String BOOLEAN = "boolean"
    static final String SCALE = "scale"
    static final String CHOICE = "choice"
    static final String TEXT = "text"
    static final List<String> ALL = [NUMERIC, BOOLEAN, SCALE, CHOICE, TEXT]
}
