package com.trevorism.model.dashboard

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class Dashboard {

    Date asOf
    List<DashboardGoal> goals = []
    List<NeedsYouItem> needsYou = []
}
