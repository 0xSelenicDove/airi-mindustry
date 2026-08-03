package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repair target validity, completion, and deterministic ordering. */
final class TargetScenarioTest extends CompanionScenarioTestSupport {
    @Test void confirmRepair() {
        final CompanionOutcome accepted = controller().handle(CompanionScenario.confirmedFreshRepair());
        assertEquals(CompanionTaskStatus.TRAVELLING, accepted.taskStatus());
        final CompanionOutcome complete = controller().handle(CompanionScenario.repairTargetRestored());
        assertEquals(CompanionTaskStatus.COMPLETE, complete.taskStatus());
        assertTrue(complete.message().contains("complete"));
        final CompanionOutcome restored = controller().handle(CompanionScenario.confirmedRepairForRestoredTarget());
        assertEquals(CompanionTaskStatus.NOT_NEEDED, restored.taskStatus());
        assertFalse(restored.message().contains("travelling"));
    }

    @Test void multipleDamagedTargetsUseStablePriorityOrder() {
        final CompanionOutcome selected = controller().handle(CompanionScenario.multipleDamagedTargets());
        assertEquals("core-adjacent-defense", selected.repairTarget().id());
        assertEquals(2, selected.remainingRepairTargets());
        assertEquals(CompanionPriority.ACTIVE_DEFENSE_REPAIR, selected.priority());
    }
}
