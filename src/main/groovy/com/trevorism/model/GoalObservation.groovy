package com.trevorism.model

class GoalObservation implements Owned {

    String id
    String ownerId
    String metricId

    Date observedAt
    Double value
    String choice
    String label
    String note
    String metricSource
    String sourceRef
    Boolean missed

    String createdBy
    Date createdDate
}
