package com.trevorism.support

import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.service.AdjustmentService
import com.trevorism.service.GoalService
import com.trevorism.service.MetricService
import com.trevorism.service.ObservationService
import com.trevorism.service.OwnedRepository

class TestStore {

    static final String OWNER = "owner-1"
    static final String OTHER_OWNER = "owner-2"

    final InMemoryRepository<Goal> goals = new InMemoryRepository<>()
    final InMemoryRepository<GoalMetric> metrics = new InMemoryRepository<>()
    final InMemoryRepository<GoalObservation> observations = new InMemoryRepository<>()
    final InMemoryRepository<GoalAdjustment> adjustments = new InMemoryRepository<>()

    final OwnedRepository<Goal> goalRepository = new OwnedRepository<>(goals, "Goal")
    final OwnedRepository<GoalMetric> metricRepository = new OwnedRepository<>(metrics, "Metric")
    final OwnedRepository<GoalObservation> observationRepository = new OwnedRepository<>(observations, "Observation")
    final OwnedRepository<GoalAdjustment> adjustmentRepository = new OwnedRepository<>(adjustments, "Adjustment")

    GoalService goalService() {
        new GoalService(goalRepository, metricRepository, observationRepository, adjustmentRepository)
    }

    MetricService metricService() {
        new MetricService(goalRepository, metricRepository, observationRepository)
    }

    ObservationService observationService() {
        new ObservationService(metricRepository, observationRepository)
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
