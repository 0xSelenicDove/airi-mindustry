package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Save/load must revalidate task and unit identities before resuming. */
final class PersistenceScenarioTest extends CompanionScenarioTestSupport {
    @Test void persistence() {
        final CompanionOutcome resumed = controller().handle(CompanionScenario.saveLoadWithValidRepairTask());
        assertEquals(CompanionTaskStatus.REPAIRING, resumed.taskStatus());
        final CompanionOutcome invalidated = controller().handle(CompanionScenario.mapRestartOrInvalidUnitReference());
        assertEquals(CompanionTaskStatus.CLEARED_AFTER_REVALIDATION, invalidated.taskStatus());
        assertFalse(invalidated.companionControlRetained());
    }
}
