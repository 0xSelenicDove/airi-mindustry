package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native-pathing eligibility; no custom route generation is permitted. */
final class PathingScenarioTest extends CompanionScenarioTestSupport {
    @Test void proposeRepair() {
        final CompanionOutcome repair = controller().handle(CompanionScenario.reachableDamagedFriendlyBuilding());
        assertEquals(CompanionTaskStatus.CONFIRMATION_REQUIRED, repair.taskStatus());
        assertEquals("REPAIR_ZONE", repair.proposal().type());
        final CompanionOutcome invalid = controller().handle(CompanionScenario.unreachableOrInvalidRepairTarget());
        assertFalse(invalid.hasProposal());
        assertTrue(invalid.message().contains("no repairable reachable target"));
    }
}
