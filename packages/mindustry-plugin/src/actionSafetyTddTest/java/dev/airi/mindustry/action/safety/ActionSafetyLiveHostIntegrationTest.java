package dev.airi.mindustry.action.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final contract for stopping an actual host-owned repair task. */
final class ActionSafetyLiveHostIntegrationTest {
    @BeforeEach void beginSession() { ActionSafetyContract.beginSession(true, 2); }

    @Test
    void stopReleasesHostPolyBeforeThePlayerIssuesItsNextCommand() {
        ActionSafetyContract.startTask("live-host-repair", "REPAIR_ZONE", 100, true);
        final var stop = ActionSafetyContract.stop(101);
        assertEquals("accepted", stop.status());
        assertTrue(stop.playerControlReleased());
    }
}
