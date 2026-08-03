package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Assigned-unit validity and interruption protections. */
final class UnitScenarioTest extends CompanionScenarioTestSupport {
    @Test void unitFailure() {
        final CompanionOutcome complete = controller().handle(CompanionScenario.polyRepairsTargetSuccessfully());
        assertEquals(CompanionTaskStatus.COMPLETE, complete.taskStatus());
        final CompanionOutcome dead = controller().handle(CompanionScenario.polyDiesDuringRepair());
        assertEquals(CompanionTaskStatus.UNIT_UNAVAILABLE, dead.taskStatus());
        final CompanionOutcome unreachable = controller().handle(CompanionScenario.pathBecomesBlockedDuringRepair());
        assertEquals(CompanionTaskStatus.UNREACHABLE, unreachable.taskStatus());
    }

    @Test void polyAlreadyBusy() {
        final CompanionOutcome busy = controller().handle(CompanionScenario.polyAlreadyBuildingOrCarryingItems());
        assertEquals(CompanionTaskStatus.UNIT_BUSY, busy.taskStatus());
        assertFalse(busy.companionControlRetained());
        assertTrue(busy.message().contains("explicit player approval"));
    }
}
