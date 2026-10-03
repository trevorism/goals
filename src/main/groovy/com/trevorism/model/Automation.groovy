package com.trevorism.model

class Automation {

    static final String MANUAL = "manual"
    static final String REMINDED = "reminded"
    static final String MEASURED = "measured"
    static final String APPROVED = "approved"
    static final String AUTOMATED = "automated"
    static final List<String> LEVELS = [MANUAL, REMINDED, MEASURED, APPROVED, AUTOMATED]

    String level
}
