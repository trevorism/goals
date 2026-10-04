package com.trevorism.model.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.progress.GoalProgress
import com.trevorism.model.progress.MetricProgress

@JsonInclude(JsonInclude.Include.ALWAYS)
class DashboardGoal {

    Goal goal
    GoalProgress progress
    GoalMetric headlineMetric
    MetricProgress headlineMetricProgress
}
