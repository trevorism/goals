package com.trevorism.service

import com.trevorism.model.types.FrequencyType

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CollectionPeriod {

    final LocalDate start
    final LocalDate nextStart
    final String frequency
    final ZoneId zone

    private CollectionPeriod(LocalDate start, String frequency, ZoneId zone) {
        this.start = start
        this.frequency = frequency
        this.zone = zone
        this.nextStart = frequency == FrequencyType.WEEKLY ? start.plusWeeks(1) : frequency == FrequencyType.MONTHLY ? start.plusMonths(1) : start.plusDays(1)
    }

    static CollectionPeriod containing(LocalDate day, String frequency, ZoneId zone) {
        String resolved = frequency ?: FrequencyType.DAILY
        new CollectionPeriod(ProgressCalculator.periodOf(day, resolved), resolved, zone)
    }

    Instant startInstant() {
        start.atStartOfDay(zone).toInstant()
    }

    Instant endInstant() {
        nextStart.atStartOfDay(zone).toInstant()
    }

    Date observedAt() {
        Date.from(start.atStartOfDay(ZoneOffset.UTC).toInstant())
    }

    boolean contains(LocalDate day) {
        !day.isBefore(start) && day.isBefore(nextStart)
    }
}
