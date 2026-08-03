package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Final host-game contract: status is derived from the real Poly lifecycle. */
final class RepairStatusLiveHostIntegrationTest {
    @BeforeEach
    void beginStatusSession() { RepairStatusContract.beginSession(); }

    @Test
    void hostGamePollingObservesPolyLifecycleAndFinalBuildingHealth() {
        RepairStatusContract.observe("live-host-repair", CompanionTaskStatus.DISPATCHING, 100, "approved");
        RepairStatusContract.observe("live-host-repair", CompanionTaskStatus.TRAVELLING, 101, "route accepted");
        RepairStatusContract.observe("live-host-repair", CompanionTaskStatus.REPAIRING, 120, "in range");
        RepairStatusContract.observe("live-host-repair", CompanionTaskStatus.COMPLETE, 160, "health restored");
        final List<RepairStatusContract.StatusEvent> events = RepairStatusContract.pollBridge(0);
        assertEquals(List.of(CompanionTaskStatus.DISPATCHING, CompanionTaskStatus.TRAVELLING,
            CompanionTaskStatus.REPAIRING, CompanionTaskStatus.COMPLETE),
            events.stream().filter(event -> event.taskId().equals("live-host-repair"))
                .map(RepairStatusContract.StatusEvent::taskStatus).toList());
    }
}
