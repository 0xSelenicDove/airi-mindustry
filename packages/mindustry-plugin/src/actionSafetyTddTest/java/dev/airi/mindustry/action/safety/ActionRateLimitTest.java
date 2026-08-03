package dev.airi.mindustry.action.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ActionRateLimitTest {
    @BeforeEach void beginSession() { ActionSafetyContract.beginSession(true, 1); }

    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalTaskWithoutSecondMutation() {
        final var first = ActionSafetyContract.submit("a-1", "REPAIR_ZONE", 100, "repair-key");
        final var retry = ActionSafetyContract.submit("a-2", "REPAIR_ZONE", 100, "repair-key");
        assertEquals(first.taskId(), retry.taskId());
        assertFalse(retry.mutationPerformed());
    }

    @Test
    void differentActionsOverPerTickLimitAreRejectedBeforeMutation() {
        ActionSafetyContract.submit("a-1", "REPAIR_ZONE", 100, "first");
        final var rejected = ActionSafetyContract.submit("a-2", "REPAIR_ZONE", 100, "second");
        assertEquals("RATE_LIMITED", rejected.reasonCode());
        assertFalse(rejected.mutationPerformed());
    }

    @Test
    void rateLimitExpiresByGameTickRatherThanWallClockTime() {
        ActionSafetyContract.submit("a-1", "REPAIR_ZONE", 100, "first");
        final var nextTick = ActionSafetyContract.submit("a-2", "REPAIR_ZONE", 101, "second");
        assertEquals("accepted", nextTick.status());
    }

    @Test
    void emergencyStopAlwaysSucceedsWhenActionLimitIsExhausted() {
        ActionSafetyContract.submit("a-1", "REPAIR_ZONE", 100, "first");
        final var stop = ActionSafetyContract.stop(100);
        assertEquals("accepted", stop.status());
    }
}
