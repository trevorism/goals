package com.trevorism.gcloud

import com.trevorism.GoalsWorld

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

World {
    new GoalsWorld()
}

After { ->
    cleanup()
}

When(~/^I GET "(.*)" anonymously$/) { String path ->
    anonGet(path)
}

When(~/^I create a goal anonymously$/) { ->
    anonPost("api/goal", [title: "anonymous", startDate: "2026-10-01", endDate: "2027-03-31"])
}

Then(~/^the request is rejected$/) { ->
    assert rejected
}

Given(~/^a root goal is created$/) { ->
    createRoot()
}

Then(~/^the root goal is active and owned by the caller$/) { ->
    assert root.id
    assert root.status == "active"
    assert root.kind == "outcome"
    assert root.ownerId
    assert root.automation.level == "manual"
}

Then(~/^the root goal is listed$/) { ->
    assert fetchRoots().any { it.id == root.id }
}

When(~/^a step is added under the root goal$/) { ->
    createChildStep()
}

When(~/^a yes\/no metric is added to the step$/) { ->
    createBooleanMetricOnChild()
}

When(~/^"yes" is recorded for the metric$/) { ->
    recordYes()
}

When(~/^an adjustment targeting the metric is recorded$/) { ->
    recordAdjustment()
}

Then(~/^the tree shows the step with its metric$/) { ->
    fetchTree()
    assert tree.goal.id == root.id
    assert tree.children.size() == 1
    Map step = tree.children[0] as Map
    assert step.goal.id == child.id
    assert step.goal.depth == 1
    assert step.goal.rootId == root.id
    assert step.metrics*.id == [metric.id]
    assert step.metrics[0].choices*.label == ["Yes", "No"]
}

Then(~/^the observation is labelled "(.*)"$/) { String label ->
    assert observation.label == label
    assert fetchObservations()*.id == [observation.id]
}

Then(~/^the adjustment is attached to the root goal$/) { ->
    assert adjustment.goalId == root.id
    assert adjustment.metricIds == [metric.id]
}

When(~/^the root goal is deleted$/) { ->
    deleteRoot()
}

Then(~/^the step and its metric are gone$/) { ->
    attemptAuthenticatedGet("api/goal/${child.id}")
    assert rejected
    attemptAuthenticatedGet("api/metric/${metric.id}")
    assert rejected
}

When(~/^I request the goal "(.*)"$/) { String id ->
    attemptAuthenticatedGet("api/goal/${id}")
}

When(~/^I add a step without a definition of done$/) { ->
    attemptAuthenticatedPost("api/goal/${root.id}/child", [title: "vague", kind: "step"])
}
