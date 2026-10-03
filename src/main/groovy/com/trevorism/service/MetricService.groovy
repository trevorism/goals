package com.trevorism.service

import com.trevorism.model.Frequency
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricChoice
import com.trevorism.model.MetricSource
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
        metric.type = metric.type ?: GoalMetric.NUMERIC
        metric.role = metric.role ?: GoalMetric.OUTCOME
        metric.enabled = metric.enabled != null ? metric.enabled : true
        metric.frequency = withFrequencyDefaults(metric.frequency)
        metric.source = metric.source?.type ? metric.source : new MetricSource(type: MetricSource.MANUAL, config: [:])
        metric.choices = normalizeChoices(metric)
        metric.direction = defaultDirection(metric)
        metric.nextDueAt = metric.nextDueAt ?: new Date()
        metric.lastCollectedAt = null
        metric.createdDate = new Date()
        validate(metric)
        metricRepository.create(ownerId, metric)
    }

    GoalMetric update(String ownerId, String id, GoalMetric changes) {
        GoalMetric existing = metricRepository.get(ownerId, id)
        existing.name = changes.name ?: existing.name
        existing.unit = changes.unit ?: existing.unit
        existing.description = changes.description ?: existing.description
        existing.role = changes.role ?: existing.role
        existing.direction = changes.direction ?: existing.direction
        existing.baseline = changes.baseline != null ? changes.baseline : existing.baseline
        existing.target = changes.target != null ? changes.target : existing.target
        existing.tolerance = changes.tolerance != null ? changes.tolerance : existing.tolerance
        existing.targetRate = changes.targetRate != null ? changes.targetRate : existing.targetRate
        existing.scaleMin = changes.scaleMin != null ? changes.scaleMin : existing.scaleMin
        existing.scaleMax = changes.scaleMax != null ? changes.scaleMax : existing.scaleMax
        existing.choices = changes.choices ?: existing.choices
        existing.frequency = changes.frequency ? withFrequencyDefaults(changes.frequency) : existing.frequency
        existing.source = changes.source?.type ? changes.source : existing.source
        existing.enabled = changes.enabled != null ? changes.enabled : existing.enabled
        require(changes.type == null || changes.type == existing.type, "a metric's type cannot change")
        existing.choices = normalizeChoices(existing)
        validate(existing)
        metricRepository.update(ownerId, id, existing)
    }

    GoalMetric delete(String ownerId, String id) {
        GoalMetric metric = metricRepository.get(ownerId, id)
        observationRepository.listWhere(ownerId, "metricId", metric.id).each { observationRepository.delete(ownerId, it.id) }
        metricRepository.delete(ownerId, id)
    }

    private static Frequency withFrequencyDefaults(Frequency frequency) {
        Frequency result = frequency ?: new Frequency()
        result.type = result.type ?: Frequency.DAILY
        result.interval = result.interval ?: 1
        result.timezone = result.timezone ?: "UTC"
        return result
    }

    private static List<MetricChoice> normalizeChoices(GoalMetric metric) {
        if (metric.type == GoalMetric.BOOLEAN) {
            return [new MetricChoice(value: "1", label: "Yes", score: 1d), new MetricChoice(value: "0", label: "No", score: 0d)]
        }
        if (metric.type != GoalMetric.CHOICE) {
            return []
        }
        List<MetricChoice> choices = metric.choices ?: []
        choices.eachWithIndex { MetricChoice choice, int index ->
            choice.value = String.valueOf(index)
        }
        return choices
    }

    private static String defaultDirection(GoalMetric metric) {
        if (metric.direction) {
            return metric.direction
        }
        metric.type in [GoalMetric.NUMERIC, GoalMetric.SCALE] ? GoalMetric.INCREASE : null
    }

    private static void validate(GoalMetric metric) {
        require(metric.name?.trim() as boolean, "name is required")
        requireOneOf(metric.type, GoalMetric.TYPES, "type")
        requireOneOf(metric.role, GoalMetric.ROLES, "role")
        requireOneOf(metric.frequency.type, Frequency.TYPES, "frequency.type")
        require(metric.frequency.interval > 0, "frequency.interval must be positive")
        requireOneOf(metric.source.type, MetricSource.TYPES, "source.type")
        if (metric.type in [GoalMetric.NUMERIC, GoalMetric.SCALE]) {
            requireOneOf(metric.direction, GoalMetric.DIRECTIONS, "direction")
        }
        if (metric.direction == GoalMetric.MAINTAIN) {
            require(metric.tolerance != null && metric.tolerance >= 0, "a maintain metric requires a non-negative tolerance")
        }
        if (metric.type == GoalMetric.SCALE) {
            require(metric.scaleMin != null && metric.scaleMax != null && metric.scaleMin < metric.scaleMax,
                    "a scale metric requires scaleMin < scaleMax")
        }
        if (metric.type == GoalMetric.BOOLEAN && metric.targetRate != null) {
            require(metric.targetRate > 0 && metric.targetRate <= 1, "targetRate must be in (0, 1]")
        }
        if (metric.type == GoalMetric.CHOICE) {
            require(metric.choices.size() >= 2, "a choice metric requires at least two choices")
            require(metric.choices.every { it.label?.trim() }, "every choice requires a label")
            require(metric.choices.every { it.score == null || (it.score >= 0 && it.score <= 1) }, "choice scores must be in [0, 1]")
        }
    }
}
