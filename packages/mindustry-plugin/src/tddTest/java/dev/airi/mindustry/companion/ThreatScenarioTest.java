package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic emergency preemption policy. */
final class ThreatScenarioTest extends CompanionScenarioTestSupport {
    @Test void repairInterruption() {
        final CompanionOutcome preempted = controller().handle(CompanionScenario.repairInterruptedByCoreThreat());
        assertEquals(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, preempted.taskStatus());
        assertTrue(preempted.message().contains("Core defense is now priority"));
        final CompanionOutcome playerOverride = controller().handle(CompanionScenario.repairInterruptedByPlayerCommand());
        assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, playerOverride.taskStatus());
    }

    @Test void newWavePreemptsOptionalRepair() {
        final CompanionOutcome preempted = controller().handle(CompanionScenario.newWaveBeginsDuringOptionalRepair());
        assertEquals(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, preempted.taskStatus());
        assertEquals(CompanionPriority.DEFENSE_READINESS, preempted.priority());
        assertTrue(preempted.shouldNotifyPlayer());
    }
}
