package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Task expiry/progress is based on game ticks, never wall-clock duration. */
final class TimingScenarioTest extends CompanionScenarioTestSupport {
    @Test void pausedGameAndLowFpsUseGameTicksNotWallClock() {
        final CompanionOutcome paused = controller().handle(CompanionScenario.gamePausedForThirtyWallClockSeconds());
        assertEquals(CompanionTaskStatus.REPAIRING, paused.taskStatus());
        assertFalse(paused.timedOut());
        final CompanionOutcome lowFps = controller().handle(CompanionScenario.lowFpsButProgressingGameTicks());
        assertEquals(CompanionTaskStatus.REPAIRING, lowFps.taskStatus());
        assertFalse(lowFps.timedOut());
    }
}
