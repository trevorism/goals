package com.trevorism.model

class GoalMetric implements Owned {

    String id
    String ownerId
    String goalId
    String rootId

    String name
    String unit
    String description
    String type
    String measures
    String direction

    Double baseline
    Double target
    Double tolerance
    Double scaleMin
    Double scaleMax
    List<Choice> choices = []

    String frequency
    String source
    Date nextDueAt
    Date lastCollectedAt
    Boolean enabled

    Date createdDate
}
