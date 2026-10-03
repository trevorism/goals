package com.trevorism.model.progress

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class GoalProgress {

    String goalId
    String parentId
    Double progress
    Double outcomeProgress
    Double effortProgress
    Double expected
    Double pace
    String status
}
