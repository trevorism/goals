package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.MetricType
import com.trevorism.model.types.PendingAskStatusType
import io.micronaut.http.exceptions.HttpStatusException
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Singleton
class AnswerIntakeService {

    static final String IGNORED = "ignored"
    static final String REJECTED = "rejected"
    static final String RECORDED = "recorded"
    static final String DUPLICATE = "duplicate"
    static final String INVALID = "invalid"

    private static final Logger log = LoggerFactory.getLogger(AnswerIntakeService)
    private static final String NUMBER = /-?\d+(?:\.\d+)?/

    private final Repository<GoalPendingAsk> pendingAskStore
    private final OwnedRepository<GoalPendingAsk> pendingAskRepository
    private final OwnedRepository<GoalMetric> metricRepository
    private final ObservationService observationService
    private final ProfileService profileService
    private final PromptClient promptClient
    Clock clock = Clock.systemUTC()

    AnswerIntakeService(@Named("pendingAskStore") Repository<GoalPendingAsk> pendingAskStore,
                        @Named("pendingAsk") OwnedRepository<GoalPendingAsk> pendingAskRepository,
                        @Named("metric") OwnedRepository<GoalMetric> metricRepository,
                        ObservationService observationService,
                        ProfileService profileService,
                        PromptClient promptClient) {
        this.pendingAskStore = pendingAskStore
        this.pendingAskRepository = pendingAskRepository
        this.metricRepository = metricRepository
        this.observationService = observationService
        this.profileService = profileService
        this.promptClient = promptClient
    }

    String questionAnswered(Map event) {
        String questionId = event?.questionId?.toString()
        String answerId = event?.answerId?.toString()
        if (!questionId || !answerId) {
            return IGNORED
        }
        GoalPendingAsk ask = pendingAskStore.filter(new SimpleFilter("questionId", FilterConstants.OPERATOR_EQUAL, questionId))
                .find { it.status == PendingAskStatusType.OPEN }
        if (ask == null) {
            return IGNORED
        }
        Map answer = promptClient.getAnswer(answerId)
        if (answer?.questionId?.toString() != questionId || answer?.identityId?.toString() != ask.ownerId) {
            log.warn("Answer ${answerId} does not belong to question ${questionId} and its owner; ignoring it")
            return REJECTED
        }
        ask.answerId = answerId
        record(ask, answer)
    }

    private String record(GoalPendingAsk ask, Map answer) {
        GoalMetric metric
        try {
            metric = metricRepository.get(ask.ownerId, ask.metricId)
        } catch (HttpStatusException ignored) {
            return resolve(ask, PendingAskStatusType.INVALID, "The metric no longer exists", INVALID)
        }
        LocalDate periodDay = ask.periodStart.toInstant().atZone(profileService.zoneFor(ask.ownerId)).toLocalDate()
        CollectionPeriod period = CollectionPeriod.containing(periodDay, metric.frequency, ZoneId.of("UTC"))
        boolean alreadyRecorded = observationService.listForMetric(ask.ownerId, metric.id)
                .any { it.observedAt != null && period.contains(it.observedAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate()) }
        if (alreadyRecorded) {
            return resolve(ask, PendingAskStatusType.ANSWERED, "A value was already recorded for this period", DUPLICATE)
        }
        GoalObservation observation = new GoalObservation(observedAt: period.observedAt(), metricSource: MetricSourceType.PROMPT,
                sourceRef: ask.answerId)
        String problem = fill(observation, metric, answer)
        if (problem) {
            return resolve(ask, PendingAskStatusType.INVALID, problem, INVALID)
        }
        try {
            observationService.create(ask.ownerId, metric.id, observation)
        } catch (HttpStatusException e) {
            return resolve(ask, PendingAskStatusType.INVALID, e.message, INVALID)
        }
        resolve(ask, PendingAskStatusType.ANSWERED, null, RECORDED)
    }

    static String fill(GoalObservation observation, GoalMetric metric, Map answer) {
        List<String> selected = (answer.selectedChoices ?: []) as List<String>
        String text = answer.text?.toString()?.trim()
        switch (metric.type) {
            case MetricType.BOOLEAN:
            case MetricType.CHOICE:
                if (!selected) return "The answer has no selected choice"
                observation.choice = selected.first()
                return null
            case MetricType.SCALE:
                if (!selected || !(selected.first() ==~ NUMBER)) return "The answer has no scale point"
                observation.value = Double.valueOf(selected.first())
                return null
            case MetricType.NUMERIC:
                if (answer.value instanceof Number) {
                    observation.value = (answer.value as Number).doubleValue()
                    return null
                }
                String number = firstNumber(text)
                if (number == null) return "Couldn't read a number from \"${text ?: ''}\"".toString()
                observation.value = Double.valueOf(number)
                return null
            case MetricType.TEXT:
                if (!text) return "The answer is empty"
                observation.label = text
                return null
            default:
                return "Unsupported metric type ${metric.type}".toString()
        }
    }

    static String firstNumber(String text) {
        if (!text) {
            return null
        }
        def matcher = text.replaceAll(/(?<=\d),(?=\d{3})/, "") =~ NUMBER
        matcher.find() ? matcher.group() : null
    }

    private String resolve(GoalPendingAsk ask, String status, String note, String outcome) {
        ask.status = status
        ask.note = note
        ask.resolvedDate = Date.from(clock.instant())
        pendingAskRepository.update(ask.ownerId, ask.id, ask)
        return outcome
    }
}
