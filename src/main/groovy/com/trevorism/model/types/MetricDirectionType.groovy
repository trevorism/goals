package com.trevorism.model.types

class MetricDirectionType {

    static final String INCREASE = "increase"
    static final String DECREASE = "decrease"
    static final String MAINTAIN = "maintain"
    static final String AT_LEAST = "at_least"
    static final String AT_MOST = "at_most"
    static final List<String> ALL = [INCREASE, DECREASE, MAINTAIN, AT_LEAST, AT_MOST]
    static final List<String> EACH_VALUE = [MAINTAIN, AT_LEAST, AT_MOST]
}
