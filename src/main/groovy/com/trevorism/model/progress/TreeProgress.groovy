package com.trevorism.model.progress

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class TreeProgress {

    Date asOf
    String today
    List<GoalProgress> goals = []
    List<MetricProgress> metrics = []
    List<MetricSegment> segments = []
}
