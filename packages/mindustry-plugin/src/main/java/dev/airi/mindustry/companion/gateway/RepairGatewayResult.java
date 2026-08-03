package dev.airi.mindustry.companion.gateway;

import dev.airi.mindustry.companion.CompanionTaskStatus;

/** Response returned by the host gateway; it never implies a task has started before it has. */
public record RepairGatewayResult(String taskId, CompanionTaskStatus status, String message) {
}
