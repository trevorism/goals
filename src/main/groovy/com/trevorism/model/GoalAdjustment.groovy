package com.trevorism.model

class GoalAdjustment implements Owned {

    String id
    String ownerId
    String goalId
    List<String> metricIds

    Date effectiveDate
    String title
    String description
    String category

    Date createdDate
}
