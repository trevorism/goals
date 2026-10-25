package com.trevorism.service

import com.trevorism.model.GoalProfile
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.ZoneId
import java.time.ZoneOffset

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER

class ProfileServiceTest {

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
