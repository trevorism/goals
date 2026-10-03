package com.trevorism.service

import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricSource
import jakarta.inject.Named
import jakarta.inject.Singleton

import static com.trevorism.service.Validation.require

@Singleton
class ObservationService {

    private final OwnedRepository<GoalMetric> metricRepository
    private final OwnedRepository<GoalObservation> observationRepository

    ObservationService(@Named("metric") OwnedRepository<GoalMetric> metricRepository,
                       @Named("observation") OwnedRepository<GoalObservation> observationRepository) {
        this.metricRepository = metricRepository
        this.observationRepository = observationRepository
    }

    List<GoalObservation> listForMetric(String ownerId, String metricId) {
        metricRepository.get(ownerId, metricId)
        observationRepository.listWhere(ownerId, "metricId", metricId).sort { it.observedAt }
    }

    GoalObservation create(String ownerId, String metricId, GoalObservation observation) {
        GoalMetric metric = metricRepository.get(ownerId, metricId)
        observation.metricId = metric.id
        observation.observedAt = observation.observedAt ?: new Date()
        observation.source = observation.source ?: MetricSource.MANUAL
        observation.missed = observation.missed ?: false
        observation.createdDate = new Date()
        normalizeAgainst(metric, observation)
        GoalObservation created = observationRepository.create(ownerId, observation)
        metric.lastCollectedAt = new Date()
        metricRepository.update(ownerId, metric.id, metric)
        return created
    }

    GoalObservation update(String ownerId, String id, GoalObservation changes) {
        GoalObservation existing = observationRepository.get(ownerId, id)
        GoalMetric metric = metricRepository.get(ownerId, existing.metricId)
        existing.observedAt = changes.observedAt ?: existing.observedAt
        existing.value = changes.value != null ? changes.value : existing.value
        existing.label = changes.label != null && metric.type == GoalMetric.TEXT ? changes.label : existing.label
        existing.note = changes.note ?: existing.note
        normalizeAgainst(metric, existing)
        observationRepository.update(ownerId, id, existing)
    }

    GoalObservation delete(String ownerId, String id) {
        observationRepository.delete(ownerId, id)
    }

    private static void normalizeAgainst(GoalMetric metric, GoalObservation observation) {
        if (observation.missed) {
            observation.value = null
            observation.label = null
            return
        }
        switch (metric.type) {
            case GoalMetric.NUMERIC:
                require(observation.value != null, "a numeric observation requires a value")
                observation.label = null
                break
            case GoalMetric.BOOLEAN:
                require(observation.value in [0d, 1d], "a boolean observation requires a value of 1 or 0")
                observation.label = observation.value == 1d ? "Yes" : "No"
                break
            case GoalMetric.SCALE:
                require(observation.value != null && observation.value >= metric.scaleMin && observation.value <= metric.scaleMax,
                        "a scale observation requires a value from ${metric.scaleMin} to ${metric.scaleMax}")
                observation.label = null
                break
            case GoalMetric.CHOICE:
                int index = observation.value != null ? observation.value.intValue() : -1
                require(observation.value == index && index >= 0 && index < metric.choices.size(),
                        "a choice observation requires the index of one of the metric's choices")
                observation.label = metric.choices[index].label
                break
            case GoalMetric.TEXT:
                require(observation.label?.trim() as boolean, "a text observation requires a label")
                observation.value = null
                break
        }
    }
}
