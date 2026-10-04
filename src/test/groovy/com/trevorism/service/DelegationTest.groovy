package com.trevorism.service

import com.trevorism.model.Goal
import com.trevorism.model.GoalDelegate
import com.trevorism.model.types.DelegateAccessType
import com.trevorism.support.TestStore
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import org.junit.jupiter.api.Test

import static com.trevorism.service.GoalServiceTest.assertBadRequest
import static com.trevorism.support.TestStore.OTHER_OWNER
import static com.trevorism.support.TestStore.OWNER
import static com.trevorism.support.TestStore.rootGoal
import static org.junit.jupiter.api.Assertions.assertThrows

class DelegationTest {

    private static final String AGENT = "agent-1"

    private final TestStore store = new TestStore()
    private final OwnerResolver resolver = new OwnerResolver(store.delegates)
    private final DelegateService delegates = new DelegateService(store.delegateRepository, store.delegates)


    private static void assertForbidden(String message, Closure action) {
        HttpStatusException error = assertThrows(HttpStatusException) { action() }
        assert error.status == HttpStatus.FORBIDDEN
        assert error.message == message
    }

    @Test
    void withoutOnBehalfOfEveryoneActsAsThemselves() {
        assert resolver.resolve(AGENT, HttpMethod.GET, null) == AGENT
        assert resolver.resolve(AGENT, HttpMethod.DELETE, AGENT) == AGENT
        assert resolver.resolve(OWNER, HttpMethod.POST, "") == OWNER
    }

    @Test
    void aDelegateWithEditAccessReadsAndWritesForTheOwnerButNeverDeletes() {
        delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude", access: DelegateAccessType.EDIT))

        assert resolver.resolve(AGENT, HttpMethod.GET, OWNER) == OWNER
        assert resolver.resolve(AGENT, HttpMethod.POST, OWNER) == OWNER
        assert resolver.resolve(AGENT, HttpMethod.PUT, OWNER) == OWNER
        assertForbidden("Only the owner can delete") { resolver.resolve(AGENT, HttpMethod.DELETE, OWNER) }
    }

    @Test
    void aReadOnlyDelegateCannotWrite() {
        delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude", access: DelegateAccessType.READ))

        assert resolver.resolve(AGENT, HttpMethod.GET, OWNER) == OWNER
        assertForbidden("Your access to these goals is read-only") { resolver.resolve(AGENT, HttpMethod.POST, OWNER) }
    }

    @Test
    void withoutAGrantFromThatOwnerTheRequestIsForbidden() {
        delegates.grant(OTHER_OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude"))

        assertForbidden("That owner hasn't given you access to their goals") { resolver.resolve(AGENT, HttpMethod.GET, OWNER) }
        assertForbidden("That owner hasn't given you access to their goals") { resolver.resolve("someone-else", HttpMethod.GET, OTHER_OWNER) }
    }

    @Test
    void recordsCreatedOnBehalfOfTheOwnerRememberTheDelegate() {
        delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude"))
        GoalService goals = store.goalService()

        store.goalRepository.currentDelegate = { AGENT } as java.util.function.Supplier<String>
        Goal delegated = goals.createRoot(resolver.resolve(AGENT, HttpMethod.POST, OWNER), rootGoal("By Claude"))
        store.goalRepository.currentDelegate = { null } as java.util.function.Supplier<String>
        Goal own = goals.createRoot(resolver.resolve(OWNER, HttpMethod.POST, null), rootGoal("By me"))

        assert delegated.ownerId == OWNER
        assert delegated.createdBy == AGENT
        assert own.createdBy == null
        assert goals.listRoots(OWNER)*.title.toSorted() == ["By Claude", "By me"]
    }

    @Test
    void ownersGrantUpdateAndRevokeAccess() {
        GoalDelegate grant = delegates.grant(OWNER, new GoalDelegate(delegateId: " ${AGENT} ", label: " Claude "))
        assert [grant.delegateId, grant.label, grant.access, grant.ownerId] == [AGENT, "Claude", DelegateAccessType.EDIT, OWNER]

        delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude (read only)", access: DelegateAccessType.READ))
        assert delegates.list(OWNER)*.label == ["Claude (read only)"]
        assert delegates.grantedTo(AGENT)*.ownerId == [OWNER]

        delegates.revoke(OWNER, grant.id)
        assert delegates.list(OWNER).isEmpty()
        assert delegates.grantedTo(AGENT).isEmpty()
    }

    @Test
    void grantsAreValidated() {
        assertBadRequest { delegates.grant(OWNER, new GoalDelegate(label: "Claude")) }
        assertBadRequest { delegates.grant(OWNER, new GoalDelegate(delegateId: OWNER, label: "Me")) }
        assertBadRequest { delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT)) }
        assertBadRequest { delegates.grant(OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude", access: "admin")) }
    }

    @Test
    void ownersCannotSeeOrRevokeEachOthersGrants() {
        GoalDelegate theirs = delegates.grant(OTHER_OWNER, new GoalDelegate(delegateId: AGENT, label: "Claude"))

        assert delegates.list(OWNER).isEmpty()
        HttpStatusException error = assertThrows(HttpStatusException) { delegates.revoke(OWNER, theirs.id) }
        assert error.status == HttpStatus.NOT_FOUND
    }
}
