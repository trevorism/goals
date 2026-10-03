package com.trevorism.model.progress

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class TreeProgress {

    Date asOf
    List<GoalProgress> goals = []
    List<MetricProgress> metrics = []
}
