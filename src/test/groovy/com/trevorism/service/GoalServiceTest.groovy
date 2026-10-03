package com.trevorism.service

import com.trevorism.model.Automation
import com.trevorism.model.Goal
import com.trevorism.model.GoalAdjustment
import com.trevorism.model.GoalKind
import com.trevorism.model.GoalMetric
import com.trevorism.model.GoalObservation
import com.trevorism.model.GoalStatus
import com.trevorism.model.GoalTreeNode
import com.trevorism.support.TestStore
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import org.junit.jupiter.api.Test

import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.day
import static com.trevorism.support.TestStore.rootGoal
import static org.junit.jupiter.api.Assertions.assertThrows

class GoalServiceTest {

    private final TestStore store = new TestStore()
    private final GoalService service = store.goalService()

    @Test
    void testCreateRootAppliesDefaults() {
        Goal root = service.createRoot(OWNER, rootGoal())

        assert root.ownerId == OWNER
        assert root.parentId == null
        assert root.rootId == null
        assert root.depth == 0
        assert root.kind == GoalKind.OUTCOME
        assert root.status == GoalStatus.ACTIVE
        assert root.weight == 1d
        assert root.automation.level == Automation.MANUAL
        assert root.createdDate
        assert root.treeRootId() == root.id
    }

    @Test
    void testCreateRootIgnoresClientSuppliedTreePosition() {
        Goal root = service.createRoot(OWNER, new Goal(title: "t", startDate: day(1), endDate: day(2), parentId: "x", rootId: "y", depth: 4))

        assert root.parentId == null
        assert root.rootId == null
        assert root.depth == 0
    }

    @Test
    void testCreateRootRequiresTitleAndOrderedDates() {
        assertBadRequest { service.createRoot(OWNER, new Goal(title: " ", startDate: day(1), endDate: day(2))) }
        assertBadRequest { service.createRoot(OWNER, new Goal(title: "t", startDate: day(1))) }
        assertBadRequest { service.createRoot(OWNER, new Goal(title: "t", startDate: day(5), endDate: day(5))) }
        assertBadRequest { service.createRoot(OWNER, new Goal(title: "t", startDate: day(1), endDate: day(2), kind: "wish")) }
        assertBadRequest { service.createRoot(OWNER, new Goal(title: "t", startDate: day(1), endDate: day(2), weight: 0)) }
        assertBadRequest { service.createRoot(OWNER, new Goal(title: "t", startDate: day(1), endDate: day(2), automation: new Automation(level: "magic"))) }
    }

    @Test
    void testCreateChildInheritsTreePositionAndDates() {
        Goal root = service.createRoot(OWNER, rootGoal())
        Goal milestone = service.createChild(OWNER, root.id, new Goal(title: "Cardio base"))
        Goal step = service.createChild(OWNER, milestone.id, new Goal(title: "Walk 30 minutes", kind: GoalKind.STEP,
                definitionOfDone: "Walked 30 minutes", startDate: day(10), endDate: day(17)))

        assert milestone.parentId == root.id
        assert milestone.rootId == root.id
        assert milestone.depth == 1
        assert milestone.kind == GoalKind.MILESTONE
        assert milestone.startDate == root.startDate
        assert milestone.endDate == root.endDate
        assert step.parentId == milestone.id
        assert step.rootId == root.id
        assert step.depth == 2
    }

    @Test
    void testChildrenAreOrderedInCreationOrderByDefault() {
        Goal root = service.createRoot(OWNER, rootGoal())

        Goal first = service.createChild(OWNER, root.id, new Goal(title: "first"))
        Goal second = service.createChild(OWNER, root.id, new Goal(title: "second"))

        assert first.sortOrder == 0
        assert second.sortOrder == 1
    }

    @Test
    void testChildDatesMustFallWithinTheParent() {
        Goal root = service.createRoot(OWNER, rootGoal())

        assertBadRequest { service.createChild(OWNER, root.id, new Goal(title: "late", endDate: new Date(day(365).time + 86_400_000L))) }
    }

    @Test
    void testAStepRequiresADefinitionOfDone() {
        Goal root = service.createRoot(OWNER, rootGoal())

        assertBadRequest { service.createChild(OWNER, root.id, new Goal(title: "vague", kind: GoalKind.STEP)) }
    }

    @Test
    void testCannotAddAChildToAnotherOwnersGoal() {
        Goal theirs = service.createRoot(OTHER_OWNER, rootGoal())

        assertNotFound { service.createChild(OWNER, theirs.id, new Goal(title: "intruder")) }
    }

    @Test
    void testListRootsReturnsOnlyTheOwnersRootGoals() {
        Goal root = service.createRoot(OWNER, rootGoal("mine"))
        service.createChild(OWNER, root.id, new Goal(title: "child"))
        service.createRoot(OTHER_OWNER, rootGoal("theirs"))

        assert service.listRoots(OWNER)*.title == ["mine"]
    }

    @Test
    void testUpdateMergesChangesAndKeepsTreePosition() {
        Goal root = service.createRoot(OWNER, rootGoal())
        Goal child = service.createChild(OWNER, root.id, new Goal(title: "before", description: "kept"))

        Goal updated = service.update(OWNER, child.id, new Goal(title: "after", parentId: "elsewhere", rootId: "elsewhere", depth: 9))

        assert updated.title == "after"
        assert updated.description == "kept"
        assert updated.parentId == root.id
        assert updated.rootId == root.id
        assert updated.depth == 1
    }

    @Test
    void testMarkingDoneRecordsTheCompletionDate() {
        Goal root = service.createRoot(OWNER, rootGoal())

        Goal done = service.update(OWNER, root.id, new Goal(status: GoalStatus.DONE))

        assert done.completedDate
    }

    @Test
    void testUpdateValidatesTheMergedGoal() {
        Goal root = service.createRoot(OWNER, rootGoal())

        assertBadRequest { service.update(OWNER, root.id, new Goal(kind: GoalKind.STEP)) }
    }

    @Test
    void testTreeNestsChildrenAndMetricsInSortOrder() {
        Goal root = service.createRoot(OWNER, rootGoal())
        Goal sleep = service.createChild(OWNER, root.id, new Goal(title: "Sleep", sortOrder: 2))
        Goal cardio = service.createChild(OWNER, root.id, new Goal(title: "Cardio", sortOrder: 1))
        service.createChild(OWNER, cardio.id, new Goal(title: "Zone 2"))
        store.metricService().create(OWNER, root.id, new GoalMetric(name: "Resting heart rate", direction: GoalMetric.DECREASE))
        store.metricService().create(OWNER, sleep.id, new GoalMetric(name: "Sleep quality", type: GoalMetric.SCALE, scaleMin: 1, scaleMax: 5))

        GoalTreeNode tree = service.tree(OWNER, root.id)

        assert tree.goal.id == root.id
        assert tree.metrics*.name == ["Resting heart rate"]
        assert tree.children*.goal*.title == ["Cardio", "Sleep"]
        assert tree.children[0].children*.goal*.title == ["Zone 2"]
        assert tree.children[1].metrics*.name == ["Sleep quality"]
    }

    @Test
    void testTreeOfAnInnerNodeReturnsOnlyItsSubtree() {
        Goal root = service.createRoot(OWNER, rootGoal())
        Goal cardio = service.createChild(OWNER, root.id, new Goal(title: "Cardio"))
        service.createChild(OWNER, root.id, new Goal(title: "Sleep"))
        service.createChild(OWNER, cardio.id, new Goal(title: "Zone 2"))

        GoalTreeNode tree = service.tree(OWNER, cardio.id)

        assert tree.goal.title == "Cardio"
        assert tree.children*.goal*.title == ["Zone 2"]
    }

    @Test
    void testDeleteRemovesTheSubtreeAndItsDataOnly() {
        Goal root = service.createRoot(OWNER, rootGoal())
        Goal cardio = service.createChild(OWNER, root.id, new Goal(title: "Cardio"))
        Goal zone2 = service.createChild(OWNER, cardio.id, new Goal(title: "Zone 2"))
        Goal sleep = service.createChild(OWNER, root.id, new Goal(title: "Sleep"))
        GoalMetric zone2Metric = store.metricService().create(OWNER, zone2.id, new GoalMetric(name: "Sessions"))
        GoalMetric sleepMetric = store.metricService().create(OWNER, sleep.id, new GoalMetric(name: "Hours"))
        store.observationService().create(OWNER, zone2Metric.id, new GoalObservation(value: 3))
        store.observationService().create(OWNER, sleepMetric.id, new GoalObservation(value: 7))
        store.adjustmentService().create(OWNER, cardio.id, new GoalAdjustment(title: "Bought a bike"))

        service.delete(OWNER, cardio.id)

        assert store.goals.store*.title.toSorted() == ["Improve healthspan", "Sleep"]
        assert store.metrics.store*.name == ["Hours"]
        assert store.observations.store*.value == [7d]
        assert store.adjustments.store.isEmpty()
    }

    @Test
    void testCannotReadOrDeleteAnotherOwnersGoal() {
        Goal theirs = service.createRoot(OTHER_OWNER, rootGoal())

        assertNotFound { service.get(OWNER, theirs.id) }
        assertNotFound { service.tree(OWNER, theirs.id) }
        assertNotFound { service.update(OWNER, theirs.id, new Goal(title: "x")) }
        assertNotFound { service.delete(OWNER, theirs.id) }
        assert store.goals.store.size() == 1
    }

    static void assertBadRequest(Closure action) {
        HttpStatusException error = assertThrows(HttpStatusException) { action() }
        assert error.status == HttpStatus.BAD_REQUEST
    }

    static void assertNotFound(Closure action) {
        HttpStatusException error = assertThrows(HttpStatusException) { action() }
        assert error.status == HttpStatus.NOT_FOUND
    }
}
