package com.trevorism.model.types

class PendingAskStatusType {

    static final String OPEN = "open"
    static final String ANSWERED = "answered"
    static final String MISSED = "missed"
    static final String INVALID = "invalid"
    static final List<String> ALL = [OPEN, ANSWERED, MISSED, INVALID]
}
