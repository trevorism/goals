package com.trevorism.model.today

import com.fasterxml.jackson.annotation.JsonInclude
import com.trevorism.model.GoalMetric

@JsonInclude(JsonInclude.Include.ALWAYS)
class TodayItem {

    GoalMetric metric
    String goalTitle
    String rootId
    String rootTitle
    Date periodStart
}
