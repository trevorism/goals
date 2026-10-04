package com.trevorism.support

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalDelegate
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.GoalProfile
import com.trevorism.service.AdjustmentService
import com.trevorism.service.GoalService
import com.trevorism.service.MetricService
import com.trevorism.service.ObservationService
import com.trevorism.service.OwnedRepository
import com.trevorism.service.ProfileService

class TestStore {

    static final String OWNER = "owner-1"
    static final String OTHER_OWNER = "owner-2"

    final InMemoryRepository<Goal> goals = new InMemoryRepository<>()
    final InMemoryRepository<GoalMetric> metrics = new InMemoryRepository<>()
    final InMemoryRepository<GoalObservation> observations = new InMemoryRepository<>()
    final InMemoryRepository<GoalAdjustment> adjustments = new InMemoryRepository<>()
    final InMemoryRepository<GoalPendingAsk> pendingAsks = new InMemoryRepository<>()
    final InMemoryRepository<GoalProfile> profiles = new InMemoryRepository<>()
    final InMemoryRepository<GoalDelegate> delegates = new InMemoryRepository<>()

    final OwnedRepository<Goal> goalRepository = new OwnedRepository<>(goals, "Goal")
    final OwnedRepository<GoalMetric> metricRepository = new OwnedRepository<>(metrics, "Metric")
    final OwnedRepository<GoalObservation> observationRepository = new OwnedRepository<>(observations, "Observation")
    final OwnedRepository<GoalAdjustment> adjustmentRepository = new OwnedRepository<>(adjustments, "Adjustment")
    final OwnedRepository<GoalPendingAsk> pendingAskRepository = new OwnedRepository<>(pendingAsks, "Pending ask")
    final OwnedRepository<GoalProfile> profileRepository = new OwnedRepository<>(profiles, "Profile")
    final OwnedRepository<GoalDelegate> delegateRepository = new OwnedRepository<>(delegates, "Delegate")

    GoalService goalService() {
        new GoalService(goalRepository, metricRepository, observationRepository, adjustmentRepository, pendingAskRepository)
    }

    MetricService metricService() {
        new MetricService(goalRepository, metricRepository, observationRepository, pendingAskRepository)
    }

    ObservationService observationService() {
        new ObservationService(metricRepository, observationRepository)
    }

    ProfileService profileService() {
        new ProfileService(profileRepository)
    }

    AdjustmentService adjustmentService() {
        new AdjustmentService(goalRepository, metricRepository, adjustmentRepository)
    }

    static Date day(int dayOfYear) {
        Date.from(java.time.LocalDate.ofYearDay(2027, dayOfYear).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
    }

    static Goal rootGoal(String title = "Improve healthspan") {
        new Goal(title: title, startDate: day(1), endDate: day(365))
    }
}
