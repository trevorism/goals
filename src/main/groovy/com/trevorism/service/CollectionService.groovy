package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.collection.CollectionResult
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricSourceType
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Singleton
class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService)

    private final Repository<GoalMetric> metricStore
    private final Repository<Goal> goalStore
    private final ObservationService observationService
    private final ProfileService profileService
    private final MeasurementReader measurementReader
    Clock clock = Clock.systemUTC()

    CollectionService(@Named("metricStore") Repository<GoalMetric> metricStore,
                      @Named("goalStore") Repository<Goal> goalStore,
                      ObservationService observationService,
                      ProfileService profileService,
                      MeasurementReader measurementReader) {
        this.metricStore = metricStore
        this.goalStore = goalStore
        this.observationService = observationService
        this.profileService = profileService
        this.measurementReader = measurementReader
    }

    CollectionResult tick() {
        CollectionResult result = new CollectionResult()
        measureDueMetrics(result)
        log.info("Collection tick: measured ${result.measured}, already recorded ${result.alreadyRecorded}, failed ${result.failed}")
        return result
    }

    private void measureDueMetrics(CollectionResult result) {
        Map<String, Double> readings = [:]
        Map<String, Exception> readFailures = [:]
        eachEnabledMetric(MetricSourceType.HTTP, result) { GoalMetric metric, Map<String, Goal> goalsById, ZoneId zone, LocalDate today ->
            measureIfDue(metric, goalsById, zone, today, { String measurementId -> readOnce(measurementId, readings, readFailures) }, result)
        }
    }

    private Double readOnce(String measurementId, Map<String, Double> readings, Map<String, Exception> readFailures) {
        if (readFailures.containsKey(measurementId)) {
            throw readFailures[measurementId]
        }
        if (!readings.containsKey(measurementId)) {
            try {
                readings[measurementId] = measurementReader.read(measurementId)
            } catch (Exception e) {
                readFailures[measurementId] = e
                throw e
            }
        }
        readings[measurementId]
    }

    private void eachEnabledMetric(String source, CollectionResult result, Closure collect) {
        List<GoalMetric> sourceMetrics = metricStore.filter(new SimpleFilter("source", FilterConstants.OPERATOR_EQUAL, source))
                .findAll { it.enabled != false }
        sourceMetrics.groupBy { it.ownerId }.each { String ownerId, List<GoalMetric> metrics ->
            Map<String, Goal> goalsById = goalStore.filter(new SimpleFilter(OwnedRepository.OWNER_FIELD, FilterConstants.OPERATOR_EQUAL, ownerId))
                    .collectEntries { [it.id, it] }
            ZoneId zone = profileService.zoneFor(ownerId)
            LocalDate today = LocalDate.now(clock.withZone(zone))
            metrics.each { GoalMetric metric ->
                try {
                    collect(metric, goalsById, zone, today)
                } catch (Exception e) {
                    result.failed++
                    log.warn("Unable to collect ${source} metric ${metric.id}: ${e.message}")
                }
            }
        }
    }

    private void measureIfDue(GoalMetric metric, Map<String, Goal> goalsById, ZoneId zone, LocalDate today,
                              Closure<Double> read, CollectionResult result) {
        if (!isOpen(goalsById[metric.goalId], goalsById, today)) {
            return
        }
        CollectionPeriod period = CollectionPeriod.containing(today, metric.frequency, zone)
        if (metric.lastObservedAt != null && period.contains(utcDay(metric.lastObservedAt))) {
            result.alreadyRecorded++
            return
        }
        observationService.create(metric.ownerId, metric.id, new GoalObservation(value: read(metric.measurementId),
                observedAt: period.observedAt(), metricSource: MetricSourceType.HTTP, sourceRef: metric.measurementId))
        result.measured++
    }

    private static boolean isOpen(Goal goal, Map<String, Goal> goalsById, LocalDate today) {
        if (goal == null || today.isBefore(utcDay(goal.startDate)) || today.isAfter(utcDay(goal.endDate))) {
            return false
        }
        for (Goal current = goal; current != null; current = goalsById[current.parentId]) {
            if (current.status != GoalStatusType.ACTIVE) {
                return false
            }
        }
        return true
    }

    private static LocalDate utcDay(Date date) {
        date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
    }
}
