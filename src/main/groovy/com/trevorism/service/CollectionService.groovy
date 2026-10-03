package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.collection.CollectionResult
import com.trevorism.model.types.GoalStatusType
import com.trevorism.model.types.MetricSourceType
import com.trevorism.model.types.PendingAskStatusType
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
    private final Repository<GoalPendingAsk> pendingAskStore
    private final OwnedRepository<GoalPendingAsk> pendingAskRepository
    private final ObservationService observationService
    private final ProfileService profileService
    private final PromptClient promptClient
    Clock clock = Clock.systemUTC()

    CollectionService(@Named("metricStore") Repository<GoalMetric> metricStore,
                      @Named("goalStore") Repository<Goal> goalStore,
                      @Named("pendingAskStore") Repository<GoalPendingAsk> pendingAskStore,
                      @Named("pendingAsk") OwnedRepository<GoalPendingAsk> pendingAskRepository,
                      ObservationService observationService,
                      ProfileService profileService,
                      PromptClient promptClient) {
        this.metricStore = metricStore
        this.goalStore = goalStore
        this.pendingAskStore = pendingAskStore
        this.pendingAskRepository = pendingAskRepository
        this.observationService = observationService
        this.profileService = profileService
        this.promptClient = promptClient
    }

    CollectionResult tick() {
        CollectionResult result = new CollectionResult()
        expireOpenAsks(result)
        askDueQuestions(result)
        log.info("Collection tick: asked ${result.asked}, already recorded ${result.alreadyRecorded}, missed ${result.missed}, failed ${result.failed}")
        return result
    }

    private void expireOpenAsks(CollectionResult result) {
        Date now = Date.from(clock.instant())
        pendingAskStore.filter(new SimpleFilter("status", FilterConstants.OPERATOR_EQUAL, PendingAskStatusType.OPEN))
                .findAll { it.periodEnd != null && !it.periodEnd.after(now) }
                .each { GoalPendingAsk ask ->
                    try {
                        markMissed(ask, now)
                        result.missed++
                    } catch (Exception e) {
                        result.failed++
                        log.warn("Unable to expire pending ask ${ask.id}: ${e.message}")
                    }
                }
    }

    private void markMissed(GoalPendingAsk ask, Date now) {
        LocalDate periodDay = ask.periodStart.toInstant().atZone(profileService.zoneFor(ask.ownerId)).toLocalDate()
        try {
            observationService.create(ask.ownerId, ask.metricId, new GoalObservation(missed: true,
                    observedAt: Date.from(periodDay.atStartOfDay(ZoneOffset.UTC).toInstant()),
                    metricSource: MetricSourceType.PROMPT, sourceRef: ask.questionId))
        } catch (Exception e) {
            log.info("No missed value recorded for pending ask ${ask.id}: ${e.message}")
        }
        ask.status = PendingAskStatusType.MISSED
        ask.resolvedDate = now
        pendingAskRepository.update(ask.ownerId, ask.id, ask)
    }

    private void askDueQuestions(CollectionResult result) {
        List<GoalMetric> promptMetrics = metricStore.filter(new SimpleFilter("source", FilterConstants.OPERATOR_EQUAL, MetricSourceType.PROMPT))
                .findAll { it.enabled != false }
        promptMetrics.groupBy { it.ownerId }.each { String ownerId, List<GoalMetric> metrics ->
            Map<String, Goal> goalsById = goalStore.filter(new SimpleFilter(OwnedRepository.OWNER_FIELD, FilterConstants.OPERATOR_EQUAL, ownerId))
                    .collectEntries { [it.id, it] }
            ZoneId zone = profileService.zoneFor(ownerId)
            LocalDate today = LocalDate.now(clock.withZone(zone))
            metrics.each { GoalMetric metric ->
                try {
                    askIfDue(metric, goalsById, zone, today, result)
                } catch (Exception e) {
                    result.failed++
                    log.warn("Unable to ask about metric ${metric.id}: ${e.message}")
                }
            }
        }
    }

    private void askIfDue(GoalMetric metric, Map<String, Goal> goalsById, ZoneId zone, LocalDate today, CollectionResult result) {
        Goal goal = goalsById[metric.goalId]
        if (!isOpen(goal, goalsById, today)) {
            return
        }
        CollectionPeriod period = CollectionPeriod.containing(today, metric.frequency, zone)
        Date periodStart = Date.from(period.startInstant())
        boolean alreadyAsked = pendingAskStore.filter(new SimpleFilter("metricId", FilterConstants.OPERATOR_EQUAL, metric.id))
                .any { it.periodStart == periodStart }
        if (alreadyAsked) {
            return
        }
        if (metric.lastObservedAt != null && period.contains(utcDay(metric.lastObservedAt))) {
            result.alreadyRecorded++
            return
        }
        String questionId = promptClient.askQuestion(PromptQuestions.build(metric, goal, period, metric.ownerId))
        pendingAskRepository.create(metric.ownerId, new GoalPendingAsk(metricId: metric.id, questionId: questionId,
                periodStart: periodStart, periodEnd: Date.from(period.endInstant()), status: PendingAskStatusType.OPEN,
                createdDate: Date.from(clock.instant())))
        result.asked++
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
