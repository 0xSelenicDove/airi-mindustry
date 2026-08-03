package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;

/** Read-only task-status projection used by the loopback HTTP endpoint. */
public final class RepairTaskStatusEndpoint {
    private RepairTaskStatusEndpoint() { }

    public static StatusReply get(final String taskId) {
        final StatusReply reply = RepairStatusBridge.current(taskId);
        return reply == null
            ? new StatusReply(404, "TASK_NOT_FOUND", null, null, "Repair task was not found.", -1, true)
            : reply;
    }

    public static StatusReply handle(final String method, final String taskId) {
        if (!"GET".equals(method)) {
            return new StatusReply(405, "METHOD_NOT_ALLOWED", null, null, "Repair task status only supports GET.", -1, true);
        }
        return get(taskId);
    }

    public record StatusReply(int httpStatus, String reasonCode, String taskId, CompanionTaskStatus taskStatus,
                              String message, long gameTick, boolean airiControlReleased) { }
}
