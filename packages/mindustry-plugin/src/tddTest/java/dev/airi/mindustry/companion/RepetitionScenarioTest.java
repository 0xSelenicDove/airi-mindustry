package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One active repair/task notification per companion and alert identity. */
final class RepetitionScenarioTest extends CompanionScenarioTestSupport {
    @Test void duplicateAdvice() {
        final CompanionOutcome first = controller().handle(CompanionScenario.repeatedExposureAlert());
        final CompanionOutcome duplicate = controller().handle(CompanionScenario.repeatedExposureAlert());
        assertTrue(first.shouldNotifyPlayer());
        assertFalse(duplicate.shouldNotifyPlayer());
        final CompanionOutcome emergency = controller().handle(CompanionScenario.higherPriorityCoreThreat());
        assertTrue(emergency.shouldNotifyPlayer());
        assertEquals(CompanionPriority.CORE_SURVIVAL, emergency.priority());
    }
}
