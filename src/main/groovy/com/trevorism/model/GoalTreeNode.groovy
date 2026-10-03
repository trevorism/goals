package com.trevorism.model

class GoalTreeNode {

    Goal goal
    List<GoalMetric> metrics = []
    List<GoalTreeNode> children = []
}
