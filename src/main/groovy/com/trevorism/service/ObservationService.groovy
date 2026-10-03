package com.trevorism.service

import com.trevorism.model.Choice
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.MetricSource
import com.trevorism.model.MetricType
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
        if (changes.choice != null) {
            existing.choice = changes.choice
            existing.value = null
        } else if (changes.value != null) {
            existing.value = changes.value
            existing.choice = null
        }
        existing.label = changes.label != null && metric.type == MetricType.TEXT ? changes.label : existing.label
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
            observation.choice = null
            observation.label = null
            return
        }
        switch (metric.type) {
            case MetricType.NUMERIC:
                require(observation.value != null, "a numeric observation requires a value")
                observation.choice = null
                observation.label = null
                break
            case MetricType.BOOLEAN:
                if (observation.choice == null && observation.value in [0d, 1d]) {
                    observation.choice = observation.value == 1d ? MetricChoices.YES : MetricChoices.NO
                }
                selectChoice(metric, observation)
                observation.value = observation.choice == MetricChoices.YES ? 1d : 0d
                break
            case MetricType.SCALE:
                require(observation.value != null && observation.value >= metric.scaleMin && observation.value <= metric.scaleMax,
                        "a scale observation requires a value from ${metric.scaleMin} to ${metric.scaleMax}")
                observation.choice = null
                observation.label = null
                break
            case MetricType.CHOICE:
                int index = selectChoice(metric, observation)
                observation.value = index
                break
            case MetricType.TEXT:
                require(observation.label?.trim() as boolean, "a text observation requires a label")
                observation.value = null
                observation.choice = null
                break
        }
    }

    private static int selectChoice(GoalMetric metric, GoalObservation observation) {
        List<Choice> choices = metric.choices ?: []
        int index = choices.findIndexOf { it.value == observation.choice }
        require(index >= 0, "the observation requires one of the metric's choices: ${choices*.value}")
        observation.label = choices[index].label
        return index
    }
}
