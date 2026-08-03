package dev.airi.mindustry.action.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class ActionAuditLogTest {
    @BeforeEach void beginSession() { ActionSafetyContract.beginSession(true, 4); }

    @Test
    void confirmedActionRecordsRequestTypeTickResultAndTaskId() {
        ActionSafetyContract.submit("repair-request", "REPAIR_ZONE", 100, "repair-key");
        final var entry = ActionSafetyContract.audit(Long.MAX_VALUE, 1).get(0);
        assertEquals("repair-request", entry.actionId());
        assertEquals("REPAIR_ZONE", entry.actionType());
        assertEquals(100, entry.gameTick());
        assertNotEquals(null, entry.taskId());
    }

    @Test
    void rejectedActionRecordsReasonWithoutClaimingMutation() {
        ActionSafetyContract.beginSession(true, 1);
        ActionSafetyContract.submit("first", "REPAIR_ZONE", 100, "first-key");
        ActionSafetyContract.submit("second", "REPAIR_ZONE", 100, "second-key");
        final var entry = ActionSafetyContract.audit(Long.MAX_VALUE, 1).get(0);
        assertEquals("RATE_LIMITED", entry.reasonCode());
        assertFalse(entry.mutationPerformed());
    }

    @Test
    void lifecycleStopAppendsAnOrderedCancellationEntry() {
        ActionSafetyContract.startTask("repair-1", "REPAIR_ZONE", 100, true);
        ActionSafetyContract.stop(101);
        final List<ActionSafetyContract.AuditEntry> entries = ActionSafetyContract.audit(Long.MAX_VALUE, 10);
        assertEquals("CANCELLED_BY_PLAYER", entries.get(0).status());
        assertEquals("repair-1", entries.get(0).taskId());
    }

    @Test
    void newestFirstPaginationIsStableWithoutDuplicateOrDroppedEntries() {
        ActionSafetyContract.submit("one", "REPAIR_ZONE", 100, "one");
        ActionSafetyContract.submit("two", "REPAIR_ZONE", 101, "two");
        final var firstPage = ActionSafetyContract.audit(Long.MAX_VALUE, 1);
        final var secondPage = ActionSafetyContract.audit(firstPage.get(0).sequence(), 1);
        assertEquals("two", firstPage.get(0).actionId());
        assertEquals("one", secondPage.get(0).actionId());
    }

    @Test
    void newSessionNeverResumesStaleUnitReferences() {
        ActionSafetyContract.submit("before-restart", "REPAIR_ZONE", 100, "old");
        final String priorSession = ActionSafetyContract.audit(Long.MAX_VALUE, 1).get(0).sessionId();
        ActionSafetyContract.beginSession(true, 4);
        ActionSafetyContract.submit("after-restart", "REPAIR_ZONE", 200, "new");
        final var current = ActionSafetyContract.audit(Long.MAX_VALUE, 1).get(0);
        assertNotEquals(priorSession, current.sessionId());
        assertEquals("after-restart", current.actionId());
    }
}
