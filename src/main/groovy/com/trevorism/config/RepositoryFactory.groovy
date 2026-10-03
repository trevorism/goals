package com.trevorism.config

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.service.OwnedRepository
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
class RepositoryFactory {

    @Singleton
    @Named("appClientSecureHttpClient")
    SecureHttpClient appClientSecureHttpClient() {
        new AppClientSecureHttpClient()
    }

    @Singleton
    @Named("goal")
    OwnedRepository<Goal> goalRepository(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new OwnedRepository<Goal>(new FastDatastoreRepository<Goal>(Goal, secureHttpClient), "Goal")
    }

    @Singleton
    @Named("metric")
    OwnedRepository<GoalMetric> metricRepository(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new OwnedRepository<GoalMetric>(new FastDatastoreRepository<GoalMetric>(GoalMetric, secureHttpClient), "Metric")
    }

    @Singleton
    @Named("observation")
    OwnedRepository<GoalObservation> observationRepository(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new OwnedRepository<GoalObservation>(new FastDatastoreRepository<GoalObservation>(GoalObservation, secureHttpClient), "Observation")
    }

    @Singleton
    @Named("adjustment")
    OwnedRepository<GoalAdjustment> adjustmentRepository(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new OwnedRepository<GoalAdjustment>(new FastDatastoreRepository<GoalAdjustment>(GoalAdjustment, secureHttpClient), "Adjustment")
    }
}
