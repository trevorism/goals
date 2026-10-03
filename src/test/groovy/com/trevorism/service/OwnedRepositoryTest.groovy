package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.support.InMemoryRepository
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import org.junit.jupiter.api.Test

import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static org.junit.jupiter.api.Assertions.assertThrows

class OwnedRepositoryTest {

    private final InMemoryRepository<Goal> store = new InMemoryRepository<>()
    private final OwnedRepository<Goal> repository = new OwnedRepository<>(store, "Goal")

    @Test
    void testCreateStampsTheOwnerAndIgnoresAClientSuppliedId() {
        Goal created = repository.create(OWNER, new Goal(id: "chosen-by-client", ownerId: OTHER_OWNER, title: "t"))

        assert created.ownerId == OWNER
        assert created.id != "chosen-by-client"
    }

    @Test
    void testListReturnsOnlyTheOwnersItems() {
        repository.create(OWNER, new Goal(title: "mine"))
        repository.create(OTHER_OWNER, new Goal(title: "theirs"))

        assert repository.list(OWNER)*.title == ["mine"]
    }

    @Test
    void testListWhereCombinesTheOwnerWithTheField() {
        repository.create(OWNER, new Goal(title: "a", parentId: "p"))
        repository.create(OWNER, new Goal(title: "b", parentId: "q"))
        repository.create(OTHER_OWNER, new Goal(title: "c", parentId: "p"))

        assert repository.listWhere(OWNER, "parentId", "p")*.title == ["a"]
    }

    @Test
    void testAnotherOwnersItemIsNotFoundForReadUpdateAndDelete() {
        Goal theirs = repository.create(OTHER_OWNER, new Goal(title: "theirs"))

        assertNotFound { repository.get(OWNER, theirs.id) }
        assertNotFound { repository.update(OWNER, theirs.id, new Goal(title: "hijacked")) }
        assertNotFound { repository.delete(OWNER, theirs.id) }
        assert store.store*.title == ["theirs"]
    }

    @Test
    void testAnUnknownIdIsNotFound() {
        assertNotFound { repository.get(OWNER, "missing") }
    }

    @Test
    void testUpdateKeepsTheIdAndOwner() {
        Goal mine = repository.create(OWNER, new Goal(title: "before"))

        Goal updated = repository.update(OWNER, mine.id, new Goal(id: "other", ownerId: OTHER_OWNER, title: "after"))

        assert updated.id == mine.id
        assert updated.ownerId == OWNER
        assert repository.get(OWNER, mine.id).title == "after"
    }

    @Test
    void testARequestWithoutAnIdentityIsForbidden() {
        HttpStatusException error = assertThrows(HttpStatusException) { repository.list(null) }
        assert error.status == HttpStatus.FORBIDDEN
        assertThrows(HttpStatusException) { repository.create("", new Goal(title: "t")) }
    }

    private static void assertNotFound(Closure action) {
        HttpStatusException error = assertThrows(HttpStatusException) { action() }
        assert error.status == HttpStatus.NOT_FOUND
    }
}
