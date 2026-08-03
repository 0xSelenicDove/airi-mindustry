package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Revalidation rules for changing repair work. */
final class FreshnessScenarioTest extends CompanionScenarioTestSupport {
    @Test void targetIsRepairedOrDestroyedBeforeConfirmation() {
        final CompanionOutcome repaired = controller().handle(CompanionScenario.repairTargetRestoredBeforeConfirmation());
        assertEquals(CompanionTaskStatus.NOT_NEEDED, repaired.taskStatus());
        assertFalse(repaired.worldMutationAttempted());
        final CompanionOutcome destroyed = controller().handle(CompanionScenario.repairTargetDestroyedBeforeConfirmation());
        assertEquals(CompanionTaskStatus.INVALID_TARGET, destroyed.taskStatus());
        assertFalse(destroyed.hasProposal());
        assertTrue(destroyed.message().contains("renewed approval"));
    }

    @Test void repairZoneBecomesDangerous() {
        final CompanionOutcome paused = controller().handle(CompanionScenario.repairZoneBecomesDangerous());
        assertEquals(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, paused.taskStatus());
        assertTrue(paused.shouldNotifyPlayer());
        assertTrue(paused.message().contains("danger"));
    }
}
