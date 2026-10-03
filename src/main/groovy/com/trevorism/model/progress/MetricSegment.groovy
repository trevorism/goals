package com.trevorism.model.progress

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class MetricSegment {

    String metricId
    String adjustmentId
    String adjustmentTitle
    Date startDate
    Date endDate
    Integer count
    Double slope
    Double adherence
}
