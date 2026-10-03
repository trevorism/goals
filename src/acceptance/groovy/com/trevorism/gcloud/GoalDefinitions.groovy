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
    assert root.ownerId
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
    assert step.goal.definitionOfDone == "Done when acceptance passes"
    assert step.goal.rootId == root.id
    assert step.metrics*.id == [metric.id]
    assert step.children == []
    assert step.metrics[0].choices*.value == ["yes", "no"]
    assert step.metrics[0].frequency == "daily"
    assert step.metrics[0].source == "manual"
    assert step.metrics[0].measures == "outcome"
    assert step.metrics[0].target == 0.8
}

Then(~/^the observation is labelled "(.*)"$/) { String label ->
    assert observation.label == label
    assert observation.choice == "yes"
    assert observation.metricSource == "manual"
    assert observation.value == 1
    assert fetchObservations()*.id == [observation.id]
}

Then(~/^the progress shows the step's metric meeting its target$/) { ->
    fetchProgress()
    assert progress.asOf
    assert progress.goals*.goalId.toSorted() == [root.id, child.id].toSorted()
    Map metricProgress = progress.metrics.find { it.metricId == metric.id }
    assert metricProgress.adherence == 1
    assert metricProgress.score == 1
    assert metricProgress.status == "on_track"
    assert metricProgress.currentStreak == 1
    Map rootProgress = progress.goals.find { it.goalId == root.id }
    assert rootProgress.effortProgress == 1
    assert rootProgress.outcomeProgress == null
}

Then(~/^the progress splits the step's metric at the adjustment$/) { ->
    fetchProgress()
    List segments = progress.segments.findAll { it.metricId == metric.id }
    assert segments*.adjustmentId == [null, adjustment.id]
    assert segments[1].adjustmentTitle == adjustment.title
}

Given(~/^a daily number metric is added to the root goal$/) { ->
    createNumericMetricOnRoot()
}

Then(~/^today lists the metric as due$/) { ->
    fetchToday()
    Map item = todayItems.find { it.metric.id == numericMetric.id }
    assert item
    assert item.rootId == root.id
    assert item.goalTitle == root.title
}

When(~/^a value is recorded for today$/) { ->
    recordNumericValue()
}

Then(~/^today no longer lists the metric$/) { ->
    fetchToday()
    assert !todayItems.any { it.metric.id == numericMetric.id }
}

When(~/^I ask what is due on "(.*)"$/) { String date ->
    attemptAuthenticatedGet("api/today?date=${date}")
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

When(~/^I add a child that ends after the root goal$/) { ->
    attemptAuthenticatedPost("api/goal/${root.id}/child", [title: "too late", endDate: GoalsWorld.daysFromToday(365)])
}
