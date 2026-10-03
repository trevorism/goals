package com.trevorism.config

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalPendingAsk
import com.trevorism.model.GoalProfile
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
    @Named("goalStore")
    Repository<Goal> goalStore(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new FastDatastoreRepository<Goal>(Goal, secureHttpClient)
    }

    @Singleton
    @Named("metricStore")
    Repository<GoalMetric> metricStore(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new FastDatastoreRepository<GoalMetric>(GoalMetric, secureHttpClient)
    }

    @Singleton
    @Named("pendingAskStore")
    Repository<GoalPendingAsk> pendingAskStore(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new FastDatastoreRepository<GoalPendingAsk>(GoalPendingAsk, secureHttpClient)
    }

    @Singleton
    @Named("profileStore")
    Repository<GoalProfile> profileStore(@Named("appClientSecureHttpClient") SecureHttpClient secureHttpClient) {
        new FastDatastoreRepository<GoalProfile>(GoalProfile, secureHttpClient)
    }

    @Singleton
    @Named("goal")
    OwnedRepository<Goal> goalRepository(@Named("goalStore") Repository<Goal> store) {
        new OwnedRepository<Goal>(store, "Goal")
    }

    @Singleton
    @Named("metric")
    OwnedRepository<GoalMetric> metricRepository(@Named("metricStore") Repository<GoalMetric> store) {
        new OwnedRepository<GoalMetric>(store, "Metric")
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

    @Singleton
    @Named("pendingAsk")
    OwnedRepository<GoalPendingAsk> pendingAskRepository(@Named("pendingAskStore") Repository<GoalPendingAsk> store) {
        new OwnedRepository<GoalPendingAsk>(store, "Pending ask")
    }

    @Singleton
    @Named("profile")
    OwnedRepository<GoalProfile> profileRepository(@Named("profileStore") Repository<GoalProfile> store) {
        new OwnedRepository<GoalProfile>(store, "Profile")
    }
}
