package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource preflight must never cannibalize other planned work. */
final class ResourcesScenarioTest extends CompanionScenarioTestSupport {
    @Test void insufficientRepairResources() {
        final CompanionOutcome insufficient = controller().handle(CompanionScenario.repairRequiresUnavailableResources());
        assertEquals(CompanionTaskStatus.INSUFFICIENT_ITEMS, insufficient.taskStatus());
        assertFalse(insufficient.worldMutationAttempted());
        assertEquals(0, insufficient.itemsSpent("copper"));
        assertTrue(insufficient.message().contains("insufficient"));
    }
}
