package com.trevorism.model.progress

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class MetricProgress {

    String metricId
    String goalId
    Double score
    Double expected
    String status
    Double current
    Integer observationCount

    Double fitSlope
    Double fitIntercept
    Double fitR2
    Integer fitCount
    Double projectedEnd
    Double projectedProgress
    Date projectedTargetDate

    Double adherence
    Integer currentStreak
    Integer bestStreak
}
