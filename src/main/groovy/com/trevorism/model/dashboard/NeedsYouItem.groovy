package com.trevorism.model.dashboard

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class NeedsYouItem {

    String type
    String rootId
    String goalId
    String metricId
    String title
    String detail
    Date date
    Integer count
}
