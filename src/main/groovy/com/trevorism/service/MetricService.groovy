package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.types.FrequencyType
import com.trevorism.model.types.MetricDirectionType
import com.trevorism.model.types.MetricMeasuresType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.MetricType
import jakarta.inject.Named
import jakarta.inject.Singleton

import static com.trevorism.service.Validation.require
import static com.trevorism.service.Validation.requireOneOf

@Singleton
class MetricService {

    private final OwnedRepository<Goal> goalRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalObservation> observationRepository

    MetricService(@Named("goal") OwnedRepository<Goal> goalRepository,
                  @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                  @Named("observation") OwnedRepository<GoalObservation> observationRepository) {
        this.goalRepository = goalRepository
        this.metricRepository = metricRepository
        this.observationRepository = observationRepository
    }

    List<GoalMetric> listForGoal(String ownerId, String goalId) {
        goalRepository.get(ownerId, goalId)
        metricRepository.listWhere(ownerId, "goalId", goalId).sort { it.createdDate }
    }

    GoalMetric get(String ownerId, String id) {
        metricRepository.get(ownerId, id)
    }

    GoalMetric create(String ownerId, String goalId, GoalMetric metric) {
        Goal goal = goalRepository.get(ownerId, goalId)
        metric.goalId = goal.id
        metric.rootId = goal.treeRootId()
        metric.type = metric.type ?: MetricType.NUMERIC
        metric.measures = metric.measures ?: MetricMeasuresType.OUTCOME
        metric.enabled = metric.enabled != null ? metric.enabled : true
        metric.frequency = metric.frequency ?: FrequencyType.DAILY
        metric.source = metric.source ?: MetricSourceType.MANUAL
        metric.choices = choicesFor(metric)
        metric.direction = defaultDirection(metric)
        metric.nextDueAt = metric.nextDueAt ?: new Date()
        metric.lastObservedAt = null
        metric.createdDate = new Date()
        validate(metric)
        metricRepository.create(ownerId, metric)
    }

    GoalMetric update(String ownerId, String id, GoalMetric changes) {
        GoalMetric existing = metricRepository.get(ownerId, id)
        require(changes.type == null || changes.type == existing.type, "a metric's type cannot change")
        existing.name = changes.name ?: existing.name
        existing.unit = changes.unit ?: existing.unit
        existing.description = changes.description ?: existing.description
        existing.measures = changes.measures ?: existing.measures
        existing.direction = changes.direction ?: existing.direction
        existing.baseline = changes.baseline != null ? changes.baseline : existing.baseline
        existing.target = changes.target != null ? changes.target : existing.target
        existing.tolerance = changes.tolerance != null ? changes.tolerance : existing.tolerance
        existing.scaleMin = changes.scaleMin != null ? changes.scaleMin : existing.scaleMin
        existing.scaleMax = changes.scaleMax != null ? changes.scaleMax : existing.scaleMax
        existing.choices = changes.choices ?: existing.choices
        existing.frequency = changes.frequency ?: existing.frequency
        existing.source = changes.source ?: existing.source
        existing.enabled = changes.enabled != null ? changes.enabled : existing.enabled
        existing.choices = choicesFor(existing)
        validate(existing)
        metricRepository.update(ownerId, id, existing)
    }

    GoalMetric delete(String ownerId, String id) {
        GoalMetric metric = metricRepository.get(ownerId, id)
        observationRepository.listWhere(ownerId, "metricId", metric.id).each { observationRepository.delete(ownerId, it.id) }
        metricRepository.delete(ownerId, id)
    }

    private static List choicesFor(GoalMetric metric) {
        if (metric.type == MetricType.BOOLEAN) {
            return MetricChoices.yesNo()
        }
        metric.type == MetricType.CHOICE ? MetricChoices.normalize(metric.choices) : []
    }

    private static String defaultDirection(GoalMetric metric) {
        if (metric.direction) {
            return metric.direction
        }
        metric.type in [MetricType.NUMERIC, MetricType.SCALE] ? MetricDirectionType.INCREASE : null
    }

    private static void validate(GoalMetric metric) {
        require(metric.name?.trim() as boolean, "name is required")
        requireOneOf(metric.type, MetricType.ALL, "type")
        requireOneOf(metric.measures, MetricMeasuresType.ALL, "measures")
        requireOneOf(metric.frequency, FrequencyType.ALL, "frequency")
        requireOneOf(metric.source, MetricSourceType.ALL, "source")
        if (metric.type in [MetricType.NUMERIC, MetricType.SCALE]) {
            requireOneOf(metric.direction, MetricDirectionType.ALL, "direction")
        }
        if (metric.direction == MetricDirectionType.MAINTAIN) {
            require(metric.tolerance != null && metric.tolerance >= 0, "a maintain metric requires a non-negative tolerance")
        }
        if (metric.type == MetricType.SCALE) {
            require(metric.scaleMin != null && metric.scaleMax != null && metric.scaleMin < metric.scaleMax,
                    "a scale metric requires scaleMin < scaleMax")
        }
        if (metric.type == MetricType.BOOLEAN && metric.target != null) {
            require(metric.target > 0 && metric.target <= 1, "a boolean metric's target is the share of yes answers, in (0, 1]")
        }
        if (metric.type == MetricType.CHOICE) {
            MetricChoices.validate(metric.choices)
        }
    }
}
