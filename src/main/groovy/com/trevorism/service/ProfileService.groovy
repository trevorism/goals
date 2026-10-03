package com.trevorism.service

import com.trevorism.model.GoalProfile
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset

import static com.trevorism.service.Validation.require

@Singleton
class ProfileService {

    static final String DEFAULT_TIMEZONE = "UTC"

    private final OwnedRepository<GoalProfile> profileRepository

    ProfileService(@Named("profile") OwnedRepository<GoalProfile> profileRepository) {
        this.profileRepository = profileRepository
    }

    GoalProfile get(String ownerId) {
        find(ownerId) ?: new GoalProfile(ownerId: ownerId, timezone: DEFAULT_TIMEZONE)
    }

    GoalProfile update(String ownerId, GoalProfile changes) {
        require(isValidZone(changes.timezone), "timezone must be an IANA zone such as America/New_York")
        GoalProfile existing = find(ownerId)
        if (existing == null) {
            return profileRepository.create(ownerId, new GoalProfile(timezone: changes.timezone, createdDate: new Date()))
        }
        existing.timezone = changes.timezone
        profileRepository.update(ownerId, existing.id, existing)
    }

    ZoneId zoneFor(String ownerId) {
        String timezone = find(ownerId)?.timezone
        isValidZone(timezone) ? ZoneId.of(timezone) : ZoneOffset.UTC
    }

    private GoalProfile find(String ownerId) {
        profileRepository.list(ownerId).min { it.createdDate }
    }

    private static boolean isValidZone(String timezone) {
        if (!timezone) {
            return false
        }
        try {
            ZoneId.of(timezone)
            return true
        } catch (DateTimeException ignored) {
            return false
        }
    }
}
