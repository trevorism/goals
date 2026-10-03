package com.trevorism.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class GoalTreeNode {

    Goal goal
    List<GoalMetric> metrics = []
    List<GoalTreeNode> children = []
}
