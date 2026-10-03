package com.trevorism.model.types

class GoalStatusType {

    static final String ACTIVE = "active"
    static final String COMPLETED = "completed"
    static final String MISSED = "missed"
    static final String ABANDONED = "abandoned"
    static final List<String> ALL = [ACTIVE, COMPLETED, MISSED, ABANDONED]
}
