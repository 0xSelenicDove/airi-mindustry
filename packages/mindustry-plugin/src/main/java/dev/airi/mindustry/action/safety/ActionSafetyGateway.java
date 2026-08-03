package dev.airi.mindustry.action.safety;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local, game-tick-based safety policy for AIRI world actions. It owns no game
 * objects itself; host-side executors register cancellation callbacks for
 * tasks they control.
 */
public final class ActionSafetyGateway {
    private static final int MAX_AUDIT_PAGE_SIZE = 100;
    private static boolean hostAuthoritative = true;
    private static int perTickLimit = 2;
    private static String sessionId = UUID.randomUUID().toString();
    private static long nextSequence;
    private static long latestTick;
    private static final Map<String, Task> activeTasks = new HashMap<>();
    private static final Map<String, Reply> idempotentActions = new HashMap<>();
    private static final Map<Long, Integer> acceptedActionsByTick = new HashMap<>();
    private static final List<AuditEntry> audit = new ArrayList<>();

    private ActionSafetyGateway() { }

    public static synchronized void beginSession(final boolean host, final int actionLimitPerTick) {
        hostAuthoritative = host;
        perTickLimit = Math.max(1, actionLimitPerTick);
        sessionId = UUID.randomUUID().toString();
        nextSequence = 0;
        latestTick = 0;
        activeTasks.clear();
        idempotentActions.clear();
        acceptedActionsByTick.clear();
        audit.clear();
    }

    /** Ensures the live mod has a safety session without clearing active work. */
    public static synchronized void ensureLiveSession(final boolean host) {
        hostAuthoritative = host;
    }

    public static synchronized void startTask(final String taskId, final String type, final long tick,
        final boolean airiControlled) {
        startTask(taskId, type, tick, airiControlled, () -> { });
    }

    public static synchronized void startTask(final String taskId, final String type, final long tick,
        final boolean airiControlled, final Runnable cancelCallback) {
        latestTick = Math.max(latestTick, tick);
        activeTasks.put(taskId, new Task(taskId, type, airiControlled, cancelCallback));
    }

    /** Reserves a mutation slot before a game executor performs a world change. */
    public static synchronized Reply reserve(final String actionId, final String type, final long tick,
        final String idempotencyKey) {
        latestTick = Math.max(latestTick, tick);
        if (!hostAuthoritative) {
            return record(actionId, type, tick, "unsupported-session", "UNSUPPORTED_SESSION", null, false);
        }
        if (!idempotencyKey.isBlank() && idempotentActions.containsKey(idempotencyKey)) {
            final Reply original = idempotentActions.get(idempotencyKey);
            return new Reply(original.status(), original.reasonCode(), original.taskId(), false);
        }
        final int count = acceptedActionsByTick.getOrDefault(tick, 0);
        if (count >= perTickLimit) {
            return record(actionId, type, tick, "rejected", "RATE_LIMITED", null, false);
        }
        acceptedActionsByTick.put(tick, count + 1);
        final Reply accepted = new Reply("accepted", "ACCEPTED", "task-" + actionId, true);
        if (!idempotencyKey.isBlank()) idempotentActions.put(idempotencyKey, accepted);
        record(actionId, type, tick, accepted.status(), accepted.reasonCode(), accepted.taskId(), true);
        return accepted;
    }

    /** Convenience contract used by deterministic tests. */
    public static synchronized Reply submit(final String actionId, final String type, final long tick,
        final String idempotencyKey) {
        final Reply reply = reserve(actionId, type, tick, idempotencyKey);
        if (reply.mutationPerformed()) startTask(reply.taskId(), type, tick, true);
        return reply;
    }

    public static synchronized StopReply stop(final long tick) {
        latestTick = Math.max(latestTick, tick);
        if (!hostAuthoritative) return new StopReply("unsupported-session", List.of(), 0, false);
        final List<String> stopped = new ArrayList<>();
        for (Task task : List.copyOf(activeTasks.values())) {
            if (!task.airiControlled) continue;
            task.cancelCallback.run();
            activeTasks.remove(task.taskId);
            stopped.add(task.taskId);
            record("stop-" + task.taskId, task.type, tick, "CANCELLED_BY_PLAYER", "EMERGENCY_STOP", task.taskId, false);
        }
        return new StopReply("accepted", List.copyOf(stopped), stopped.size(), true);
    }

    /** Records non-submit lifecycle transitions from host executors. */
    public static synchronized void recordLifecycle(final String taskId, final String type, final long tick,
        final String status, final String reasonCode) {
        latestTick = Math.max(latestTick, tick);
        record("lifecycle-" + taskId + "-" + tick, type, tick, status, reasonCode, taskId, false);
        if (terminal(status)) activeTasks.remove(taskId);
    }

    /** Newest-first, cursor-exclusive pagination. */
    public static synchronized List<AuditEntry> audit(final long beforeSequence, final int requestedLimit) {
        final int limit = Math.max(1, Math.min(MAX_AUDIT_PAGE_SIZE, requestedLimit));
        return audit.stream().filter(entry -> entry.sequence < beforeSequence)
            .sorted(Comparator.comparingLong(AuditEntry::sequence).reversed()).limit(limit).toList();
    }

    /** Minimal HTTP routing data; the mod owns serialization and game-thread dispatch. */
    public static synchronized HttpReply handleHttp(final String method, final String path) {
        if ("POST".equals(method) && "/v1/action/stop".equals(path)) {
            final StopReply stopped = stop(latestTick);
            return new HttpReply(stopped.status.equals("accepted") ? 200 : 409,
                stopped.status.equals("accepted") ? "ACCEPTED" : "UNSUPPORTED_SESSION", stopped.stoppedTaskIds, 0);
        }
        if ("GET".equals(method) && path.startsWith("/v1/audit")) {
            return new HttpReply(200, "ACCEPTED", List.of(), parseLimit(path));
        }
        return new HttpReply(405, "METHOD_NOT_ALLOWED", List.of(), 0);
    }

    private static int parseLimit(final String path) {
        final int marker = path.indexOf("limit=");
        if (marker < 0) return 25;
        try { return Math.max(1, Math.min(MAX_AUDIT_PAGE_SIZE, Integer.parseInt(path.substring(marker + 6).split("&", 2)[0]))); }
        catch (NumberFormatException ignored) { return 25; }
    }

    private static boolean terminal(final String status) {
        return "COMPLETE".equals(status) || "CANCELLED_BY_PLAYER".equals(status) || "INVALID_TARGET".equals(status);
    }

    private static Reply record(final String actionId, final String type, final long tick, final String status,
        final String reasonCode, final String taskId, final boolean mutation) {
        audit.add(new AuditEntry(++nextSequence, actionId, type, tick, status, reasonCode, taskId, mutation, sessionId));
        return new Reply(status, reasonCode, taskId, mutation);
    }

    private record Task(String taskId, String type, boolean airiControlled, Runnable cancelCallback) { }
    public record Reply(String status, String reasonCode, String taskId, boolean mutationPerformed) { }
    public record StopReply(String status, List<String> stoppedTaskIds, int stoppedCount, boolean playerControlReleased) { }
    public record AuditEntry(long sequence, String actionId, String actionType, long gameTick, String status,
                             String reasonCode, String taskId, boolean mutationPerformed, String sessionId) { }
    public record HttpReply(int httpStatus, String reasonCode, List<String> stoppedTaskIds, int pageSize) { }
}
