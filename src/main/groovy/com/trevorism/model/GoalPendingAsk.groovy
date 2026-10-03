package com.trevorism.model

class GoalPendingAsk implements Owned {

    String id
    String ownerId
    String metricId
    String questionId
    String answerId

    Date periodStart
    Date periodEnd
    String status
    String note

    Date createdDate
    Date resolvedDate
}
