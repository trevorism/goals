package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import com.trevorism.model.GoalProfile
import com.trevorism.support.TestStore
import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER

class ProvisioningAndProfileTest {

    private final List<List> posts = []

    private ProvisioningService provisioning(String subscriptionsJson, String tasksJson) {
        SecureHttpClient client = [
                get : { String url -> url.endsWith("/subscription") ? subscriptionsJson : tasksJson },
                post: { String url, String body -> posts << [url, new JsonSlurper().parseText(body)]; "{}" }
        ] as SecureHttpClient
        ProvisioningService service = new ProvisioningService(client, "https://goals.example.com")
        service.clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
        return service
    }

    @Test
    void testProvisioningCreatesTheSubscriptionAndDailyTickWhenMissing() {
        Map result = provisioning("[]", '[{"name":"someone_else"}]').provision()

        assert result == [subscription: ProvisioningService.CREATED, schedule: ProvisioningService.CREATED]
        assert posts[0][0] == "https://event.data.trevorism.com/subscription"
        assert posts[0][1] == [name: "goals-question-answered", topic: "questionAnswered", url: "https://goals.example.com/api/event/questionAnswered"]
        assert posts[1][0] == "https://schedule.action.trevorism.com/api/schedule"
        assert posts[1][1].name == "goals_daily_tick"
        assert posts[1][1].type == "daily"
        assert posts[1][1].endpoint == "https://goals.example.com/api/collect/tick"
        assert posts[1][1].httpMethod == "post"
        assert posts[1][1].startDate == "2026-10-04T11:07:00Z"
    }

    @Test
    void testProvisioningIsIdempotent() {
        Map result = provisioning('[{"name":"goals-question-answered"}]', '[{"name":"goals_daily_tick"}]').provision()

        assert result == [subscription: ProvisioningService.EXISTS, schedule: ProvisioningService.EXISTS]
        assert posts.isEmpty()
    }

    @Test
    void testTheNextTickIsTodayWhenItHasNotHappenedYet() {
        ProvisioningService service = provisioning("[]", "[]")
        service.clock = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC)

        assert service.nextTickTime() == "2026-10-03T11:07:00Z"
    }

    @Test
    void testAProfileDefaultsToUtcAndStoresAValidTimezone() {
        TestStore store = new TestStore()
        ProfileService profiles = store.profileService()

        assert profiles.get(OWNER).timezone == "UTC"
        assert profiles.zoneFor(OWNER) == ZoneOffset.UTC

        profiles.update(OWNER, new GoalProfile(timezone: "America/New_York"))
        profiles.update(OWNER, new GoalProfile(timezone: "America/Chicago"))

        assert store.profiles.store.size() == 1
        assert profiles.get(OWNER).timezone == "America/Chicago"
        assert profiles.zoneFor(OWNER) == ZoneId.of("America/Chicago")
        assert profiles.zoneFor(OTHER_OWNER) == ZoneOffset.UTC
        assertBadRequest { profiles.update(OWNER, new GoalProfile(timezone: "Mars/Olympus")) }
        assertBadRequest { profiles.update(OWNER, new GoalProfile()) }
    }
}
