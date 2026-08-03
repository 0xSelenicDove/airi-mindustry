package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test maps directly to one bridge-event lifecycle and UX outcome. */
final class RepairStatusBridgeEventTest {
    @BeforeEach
    void beginStatusSession() { RepairStatusContract.beginSession(); }

    @Test
    void statusChangesEmitOneOrderedLifecycleSequence() {
        RepairStatusContract.observe("repair-1", CompanionTaskStatus.DISPATCHING, 100, "approved");
        RepairStatusContract.observe("repair-1", CompanionTaskStatus.TRAVELLING, 101, "route accepted");
        RepairStatusContract.observe("repair-1", CompanionTaskStatus.REPAIRING, 120, "in range");
        RepairStatusContract.observe("repair-1", CompanionTaskStatus.COMPLETE, 160, "health restored");
        final List<RepairStatusContract.StatusEvent> events = RepairStatusContract.pollBridge(0);
        assertEquals(List.of(CompanionTaskStatus.DISPATCHING, CompanionTaskStatus.TRAVELLING,
            CompanionTaskStatus.REPAIRING, CompanionTaskStatus.COMPLETE),
            events.stream().map(RepairStatusContract.StatusEvent::taskStatus).toList());
    }

    @Test
    void repeatedUnchangedStatusDoesNotEmitDuplicateEvent() {
        RepairStatusContract.observe("repair-2", CompanionTaskStatus.TRAVELLING, 200, "route accepted");
        RepairStatusContract.observe("repair-2", CompanionTaskStatus.TRAVELLING, 201, "route accepted");
        assertEquals(1, RepairStatusContract.pollBridge(0).stream()
            .filter(event -> event.taskId().equals("repair-2")).count());
    }

    @Test
    void newWaveOrCoreDangerEmitsOneEmergencyPauseWithTickAndReason() {
        RepairStatusContract.observe("repair-3", CompanionTaskStatus.PAUSED_FOR_EMERGENCY, 300, "new-wave");
        final List<RepairStatusContract.StatusEvent> events = RepairStatusContract.pollBridge(0);
        final var event = events.get(events.size() - 1);
        assertEquals(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, event.taskStatus());
        assertEquals(300, event.gameTick());
        assertEquals("new-wave", event.reason());
    }

    @Test
    void repairResumesOnlyAfterLocalRevalidation() {
        RepairStatusContract.observe("repair-4", CompanionTaskStatus.PAUSED_FOR_EMERGENCY, 400, "core-damage");
        RepairStatusContract.observe("repair-4", CompanionTaskStatus.REPAIRING, 401, "revalidated");
        final List<RepairStatusContract.StatusEvent> events = RepairStatusContract.pollBridge(0);
        final var event = events.get(events.size() - 1);
        assertEquals(CompanionTaskStatus.REPAIRING, event.taskStatus());
        assertEquals("revalidated", event.reason());
    }

    @Test
    void bridgeRestartRestoresCurrentTaskWithoutReplayingOldEvents() {
        RepairStatusContract.observe("repair-5", CompanionTaskStatus.TRAVELLING, 500, "route accepted");
        final var reply = RepairStatusContract.resumeAfterBridgeRestart("repair-5");
        assertEquals("repair-5", reply.taskId());
        assertTrue(RepairStatusContract.pollBridge(Long.MAX_VALUE).isEmpty());
    }
}
