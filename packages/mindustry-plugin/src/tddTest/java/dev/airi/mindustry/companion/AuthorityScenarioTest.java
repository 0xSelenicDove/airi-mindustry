package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player ownership and host-authority rules. */
final class AuthorityScenarioTest extends CompanionScenarioTestSupport {
    @Test void assignCompanion() {
        final CompanionOutcome assigned = controller().handle(CompanionScenario.assignEligiblePoly());
        assertEquals(CompanionTaskStatus.IDLE, assigned.taskStatus());
        assertTrue(assigned.message().contains("Poly assigned"));
        final CompanionOutcome unavailable = controller().handle(CompanionScenario.assignWithoutEligiblePoly());
        assertEquals(CompanionTaskStatus.UNIT_UNAVAILABLE, unavailable.taskStatus());
        assertTrue(unavailable.message().contains("no eligible Poly"));
    }

    @Test void playerCancelsWhileTravellingOrRepairing() {
        final CompanionOutcome travelling = controller().handle(CompanionScenario.playerCancelsTravellingRepair());
        assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, travelling.taskStatus());
        assertFalse(travelling.companionControlRetained());
        final CompanionOutcome repairing = controller().handle(CompanionScenario.playerCancelsActiveRepair());
        assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, repairing.taskStatus());
        assertFalse(repairing.companionControlRetained());
    }

    @Test void playerRepurposesAssignedPoly() {
        final CompanionOutcome outcome = controller().handle(CompanionScenario.playerRepurposesAssignedPoly());
        assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, outcome.taskStatus());
        assertFalse(outcome.companionControlRetained());
        assertTrue(outcome.message().contains("player command"));
    }

    @Test void multiplayer() {
        final CompanionOutcome host = controller().handle(CompanionScenario.hostAuthoritativeRepair());
        assertEquals(CompanionTaskStatus.TRAVELLING, host.taskStatus());
        assertTrue(host.worldMutationAttempted());
        final CompanionOutcome client = controller().handle(CompanionScenario.clientOnlySharedWorldRepair());
        assertEquals(CompanionTaskStatus.UNSUPPORTED_SESSION, client.taskStatus());
        assertFalse(client.worldMutationAttempted());
        assertTrue(client.message().contains("host"));
    }
}
