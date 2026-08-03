package dev.airi.mindustry.action.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmergencyStopActionTest {
    @BeforeEach void beginSession() { ActionSafetyContract.beginSession(true, 2); }

    @Test
    void stopWithTravellingRepairCancelsTaskAndReleasesPolyImmediately() {
        ActionSafetyContract.startTask("repair-1", "REPAIR_ZONE", 100, true);
        final var reply = ActionSafetyContract.stop(101);
        assertEquals("accepted", reply.status());
        assertEquals(1, reply.stoppedCount());
        assertTrue(reply.playerControlReleased());
    }

    @Test
    void stopCancelsEveryActiveAiriTaskWithoutChangingPlayerWork() {
        ActionSafetyContract.startTask("repair-1", "REPAIR_ZONE", 100, true);
        ActionSafetyContract.startTask("blueprint-1", "PLACE_BLUEPRINT", 100, true);
        ActionSafetyContract.startTask("player-work", "PLAYER_BUILD", 100, false);
        final var reply = ActionSafetyContract.stop(101);
        assertEquals(2, reply.stoppedCount());
        assertTrue(reply.stoppedTaskIds().containsAll(java.util.List.of("repair-1", "blueprint-1")));
        assertFalse(reply.stoppedTaskIds().contains("player-work"));
    }

    @Test
    void stopWithNoActiveTasksIsIdempotentlyAccepted() {
        final var reply = ActionSafetyContract.stop(101);
        assertEquals("accepted", reply.status());
        assertEquals(0, reply.stoppedCount());
    }

    @Test
    void clientOnlySessionDoesNotClaimLocalUnitControlWasReleased() {
        ActionSafetyContract.beginSession(false, 2);
        final var reply = ActionSafetyContract.stop(101);
        assertEquals("unsupported-session", reply.status());
        assertFalse(reply.playerControlReleased());
    }
}
