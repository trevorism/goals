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
    String source
    String sourceRef
    Boolean missed

    Date createdDate
}
