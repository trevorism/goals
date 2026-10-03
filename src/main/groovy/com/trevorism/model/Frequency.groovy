package com.trevorism.model

class Frequency {

    static final String DAILY = "daily"
    static final String WEEKLY = "weekly"
    static final String MONTHLY = "monthly"
    static final String EVERY_N_DAYS = "everyndays"
    static final List<String> TYPES = [DAILY, WEEKLY, MONTHLY, EVERY_N_DAYS]

    String type
    Integer interval
    Integer dayOfWeek
    Integer dayOfMonth
    String timeOfDay
    String timezone
}
