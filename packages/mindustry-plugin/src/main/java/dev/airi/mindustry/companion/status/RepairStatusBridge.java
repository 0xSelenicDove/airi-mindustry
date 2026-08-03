package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory, tick-based repair-status feed. It is written only by game-thread
 * code and read by the loopback bridge, so it never waits for model inference.
 */
public final class RepairStatusBridge {
    private static final Map<String, RepairTaskStatusEndpoint.StatusReply> current = new HashMap<>();
    private static final List<StatusEvent> events = new ArrayList<>();
    private static long nextSequence;

    private RepairStatusBridge() { }

    public static synchronized void observe(final String taskId, final CompanionTaskStatus taskStatus,
        final long gameTick, final String reason) {
        final RepairTaskStatusEndpoint.StatusReply previous = current.get(taskId);
        if (previous != null && previous.taskStatus() == taskStatus && previous.message().equals(reason)) return;
        final boolean released = releasesControl(taskStatus);
        final RepairTaskStatusEndpoint.StatusReply reply = new RepairTaskStatusEndpoint.StatusReply(
            200, taskStatus.name(), taskId, taskStatus, reason, gameTick, released);
        current.put(taskId, reply);
        events.add(new StatusEvent(++nextSequence, taskId, taskStatus, gameTick, reason));
    }

    public static synchronized List<StatusEvent> eventsAfter(final long afterSequence) {
        return events.stream().filter(event -> event.sequence() > afterSequence)
            .sorted(Comparator.comparingLong(StatusEvent::sequence)).toList();
    }

    public static synchronized RepairTaskStatusEndpoint.StatusReply restoreCurrentTask(final String taskId) {
        return current(taskId);
    }

    static synchronized RepairTaskStatusEndpoint.StatusReply current(final String taskId) {
        return current.get(taskId);
    }

    /** Invoked when a new world/session begins; saved unit references are never resumed implicitly. */
    public static synchronized void resetSession() {
        current.clear();
        events.clear();
        nextSequence = 0;
    }

    private static boolean releasesControl(final CompanionTaskStatus status) {
        return status == CompanionTaskStatus.COMPLETE || status == CompanionTaskStatus.CANCELLED_BY_PLAYER
            || status == CompanionTaskStatus.INVALID_TARGET || status == CompanionTaskStatus.UNIT_UNAVAILABLE
            || status == CompanionTaskStatus.UNREACHABLE || status == CompanionTaskStatus.PAUSED_FOR_EMERGENCY;
    }

    public record StatusEvent(long sequence, String taskId, CompanionTaskStatus taskStatus, long gameTick, String reason) { }
}
