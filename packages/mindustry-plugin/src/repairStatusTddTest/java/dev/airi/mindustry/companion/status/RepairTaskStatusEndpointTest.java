package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test maps directly to one player-visible status endpoint outcome. */
final class RepairTaskStatusEndpointTest {
    @BeforeEach
    void beginStatusSession() { RepairStatusContract.beginSession(); }

    @Test
    void knownTaskIdReturnsLifecycleStatusAndGameTick() {
        RepairStatusContract.observe("repair-1", CompanionTaskStatus.TRAVELLING, 100, "route accepted");
        final var reply = RepairStatusContract.get("repair-1");
        assertEquals(200, reply.httpStatus());
        assertEquals("repair-1", reply.taskId());
        assertEquals(CompanionTaskStatus.TRAVELLING, reply.taskStatus());
        assertTrue(reply.gameTick() >= 0);
    }

    @Test
    void unknownTaskIdReturnsTaskNotFoundWithoutCreatingTask() {
        final var reply = RepairStatusContract.get("missing-task");
        assertEquals(404, reply.httpStatus());
        assertEquals("TASK_NOT_FOUND", reply.reasonCode());
    }

    @Test
    void wrongHttpMethodReturnsMethodNotAllowed() {
        final var reply = RepairStatusContract.getWithMethod("POST", "repair-1");
        assertEquals(405, reply.httpStatus());
        assertEquals("METHOD_NOT_ALLOWED", reply.reasonCode());
    }

    @Test
    void completedTaskNeverClaimsItIsStillRepairing() {
        RepairStatusContract.observe("completed-repair", CompanionTaskStatus.COMPLETE, 160, "health restored");
        final var reply = RepairStatusContract.get("completed-repair");
        assertEquals(CompanionTaskStatus.COMPLETE, reply.taskStatus());
    }

    @Test
    void playerCancelledOrRepurposedTaskReportsReleasedControl() {
        RepairStatusContract.observe("cancelled-repair", CompanionTaskStatus.CANCELLED_BY_PLAYER, 160, "player repurposed Poly");
        final var reply = RepairStatusContract.get("cancelled-repair");
        assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, reply.taskStatus());
        assertTrue(reply.airiControlReleased());
    }

    @Test
    void clientOnlySessionCannotInventRepairTaskStatus() {
        final var reply = RepairStatusContract.get("client-rejected-repair");
        assertEquals(404, reply.httpStatus());
        assertEquals("TASK_NOT_FOUND", reply.reasonCode());
    }
}
