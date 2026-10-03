package com.trevorism.model.types

class ProgressStatusType {

    static final String AHEAD = "ahead"
    static final String ON_TRACK = "on_track"
    static final String AT_RISK = "at_risk"
    static final String BEHIND = "behind"
    static final String NO_DATA = "no_data"
    static final List<String> ALL = [AHEAD, ON_TRACK, AT_RISK, BEHIND, NO_DATA]
}
