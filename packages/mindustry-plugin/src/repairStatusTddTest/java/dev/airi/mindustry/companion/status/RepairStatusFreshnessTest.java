package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tick-based freshness and invalidation requirements for repair status. */
final class RepairStatusFreshnessTest {
    @BeforeEach
    void beginStatusSession() { RepairStatusContract.beginSession(); }

    @Test
    void pausedGameAndLowFpsUseGameTicksRatherThanWallClockExpiry() {
        RepairStatusContract.observe("repair-6", CompanionTaskStatus.TRAVELLING, 500, "route accepted");
        final var reply = RepairStatusContract.get("repair-6");
        assertEquals(500, reply.gameTick());
        assertEquals(CompanionTaskStatus.TRAVELLING, reply.taskStatus());
    }

    @Test
    void destroyedTargetWhileTravellingInvalidatesTaskAndReleasesPoly() {
        RepairStatusContract.observe("repair-7", CompanionTaskStatus.INVALID_TARGET, 600, "target-destroyed");
        final var reply = RepairStatusContract.get("repair-7");
        assertEquals(CompanionTaskStatus.INVALID_TARGET, reply.taskStatus());
        assertTrue(reply.airiControlReleased());
    }
}
