package com.trevorism.model

class GoalStatus {

    static final String DRAFT = "draft"
    static final String ACTIVE = "active"
    static final String DONE = "done"
    static final String MISSED = "missed"
    static final String ABANDONED = "abandoned"
    static final List<String> ALL = [DRAFT, ACTIVE, DONE, MISSED, ABANDONED]
}
