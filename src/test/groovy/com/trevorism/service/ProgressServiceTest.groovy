package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.progress.TreeProgress
import com.trevorism.support.TestStore
import org.junit.jupiter.api.Test

import java.time.Clock
import java.time.ZoneOffset

import static com.trevorism.service.GoalServiceTest.assertNotFound
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal

class ProgressServiceTest {

    private final TestStore store = new TestStore()
    private final ProgressService service = new ProgressService(store.goalRepository, store.metricRepository, store.observationRepository)

    ProgressServiceTest() {
        service.clock = Clock.fixed(day(183).toInstant(), ZoneOffset.UTC)
    }

    @Test
    void testProgressCoversTheRequestedSubtreeWithItsMetrics() {
        Goal root = store.goalService().createRoot(OWNER, rootGoal())
        Goal cardio = store.goalService().createChild(OWNER, root.id, new Goal(title: "Cardio"))
        Goal zone2 = store.goalService().createChild(OWNER, cardio.id, new Goal(title: "Zone 2"))
        store.goalService().createChild(OWNER, root.id, new Goal(title: "Sleep"))
        GoalMetric sessions = store.metricService().create(OWNER, zone2.id, new GoalMetric(name: "Sessions", baseline: 0, target: 10))
        store.observationService().create(OWNER, sessions.id, new GoalObservation(value: 5, observedAt: day(100)))

        TreeProgress progress = service.progress(OWNER, cardio.id)

        assert progress.goals*.goalId.toSorted() == [cardio.id, zone2.id].toSorted()
        assert progress.metrics*.metricId == [sessions.id]
        assert progress.metrics[0].score == 0.5d
        assert progress.asOf == day(183)
    }

    @Test
    void testAnotherOwnersGoalIsNotFound() {
        Goal theirs = store.goalService().createRoot(OTHER_OWNER, rootGoal())

        assertNotFound { service.progress(OWNER, theirs.id) }
    }
}
