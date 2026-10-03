package com.trevorism.model

class GoalMetric implements Owned {

    static final String NUMERIC = "numeric"
    static final String BOOLEAN = "boolean"
    static final String SCALE = "scale"
    static final String CHOICE = "choice"
    static final String TEXT = "text"
    static final List<String> TYPES = [NUMERIC, BOOLEAN, SCALE, CHOICE, TEXT]

    static final String OUTCOME = "outcome"
    static final String EFFORT = "effort"
    static final List<String> ROLES = [OUTCOME, EFFORT]

    static final String INCREASE = "increase"
    static final String DECREASE = "decrease"
    static final String MAINTAIN = "maintain"
    static final List<String> DIRECTIONS = [INCREASE, DECREASE, MAINTAIN]

    String id
    String ownerId
    String goalId
    String rootId

    String name
    String unit
    String description
    String type
    String role
    String direction

    Double baseline
    Double target
    Double tolerance
    Double targetRate
    Double scaleMin
    Double scaleMax
    List<MetricChoice> choices

    Frequency frequency
    MetricSource source
    Date nextDueAt
    Date lastCollectedAt
    Boolean enabled

    Date createdDate
}
