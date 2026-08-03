package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player-visible language must match the gateway-confirmed task state. */
final class MessagingUxScenarioTest extends CompanionScenarioTestSupport {
    @Test void dispatchingMessageWaitsForGatewayAssignment() {
        final CompanionOutcome dispatching = controller().handle(CompanionScenario.repairApprovedBeforeGatewayAssignment());
        assertEquals(CompanionTaskStatus.DISPATCHING, dispatching.taskStatus());
        assertTrue(dispatching.message().contains("dispatching"));
        assertFalse(dispatching.message().contains("I’m repairing"));
        final CompanionOutcome assigned = controller().handle(CompanionScenario.gatewayConfirmsRepairAssignment());
        assertEquals(CompanionTaskStatus.TRAVELLING, assigned.taskStatus());
        assertTrue(assigned.message().contains("travelling"));
    }
}
