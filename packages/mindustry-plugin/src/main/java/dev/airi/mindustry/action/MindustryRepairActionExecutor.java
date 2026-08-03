package dev.airi.mindustry.action;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import dev.airi.mindustry.companion.gateway.HostRepairGateway;
import dev.airi.mindustry.companion.gateway.MindustryRepairWorld;
import dev.airi.mindustry.companion.gateway.RepairGatewayResult;
import dev.airi.mindustry.companion.gateway.RepairZoneCommand;
import dev.airi.mindustry.companion.status.RepairStatusBridge;
import dev.airi.mindustry.action.safety.ActionSafetyGateway;

import java.util.HashMap;
import java.util.Map;

/** Converts the public REPAIR_ZONE action into a host-owned Poly task. */
final class MindustryRepairActionExecutor implements RepairActionExecutor {
    private final MindustryRepairWorld repairWorld = new MindustryRepairWorld();
    private final HostRepairGateway gateway = new HostRepairGateway(repairWorld);
    private final Map<String, BlueprintExecutor.ActionResult> idempotentResults = new HashMap<>();
    private final Map<String, Long> activeTaskTicks = new HashMap<>();
    private final Map<String, String> lastTaskStatuses = new HashMap<>();

    @Override
    public BlueprintExecutor.ActionResult submit(final int polyId, final int buildingId, final long refTick,
        final boolean confirmed, final String idempotencyKey) {
        ActionSafetyGateway.ensureLiveSession(repairWorld.isHostAuthoritative());
        if (!idempotencyKey.isBlank() && idempotentResults.containsKey(idempotencyKey)) {
            return idempotentResults.get(idempotencyKey);
        }
        final RepairGatewayResult result = gateway.submit(new RepairZoneCommand(polyId, buildingId, refTick, confirmed));
        final BlueprintExecutor.ActionResult actionResult = toActionResult(result, refTick);
        if (result.taskId() != null) {
            activeTaskTicks.put(result.taskId(), refTick);
            RepairStatusBridge.observe(result.taskId(), result.status(), refTick, result.message());
            ActionSafetyGateway.startTask(result.taskId(), "REPAIR_ZONE", refTick, true, () -> {
                final RepairGatewayResult cancelled = gateway.cancel(result.taskId(), refTick);
                RepairStatusBridge.observe(result.taskId(), cancelled.status(), refTick, cancelled.message());
            });
            lastTaskStatuses.put(result.taskId(), result.status().name());
            if (!idempotencyKey.isBlank()) idempotentResults.put(idempotencyKey, actionResult);
        }
        return actionResult;
    }

    @Override
    public void refresh(final long currentTick) {
        for (String taskId : activeTaskTicks.keySet()) {
            final RepairGatewayResult result = gateway.status(taskId);
            RepairStatusBridge.observe(taskId, result.status(), currentTick, result.message());
            if (!result.status().name().equals(lastTaskStatuses.get(taskId))) {
                ActionSafetyGateway.recordLifecycle(taskId, "REPAIR_ZONE", currentTick, result.status().name(), result.status().name());
                lastTaskStatuses.put(taskId, result.status().name());
            }
        }
    }

    private static BlueprintExecutor.ActionResult toActionResult(final RepairGatewayResult result, final long acceptedAtTick) {
        final String status = switch (result.status()) {
            case DISPATCHING -> "dispatching";
            case TRAVELLING -> "travelling";
            case REPAIRING -> "repairing";
            case COMPLETE -> "complete";
            case CONFIRMATION_REQUIRED -> "confirmation-required";
            default -> "rejected";
        };
        final String reasonCode = result.status() == CompanionTaskStatus.DISPATCHING ? "DISPATCHING" : result.status().name();
        return new BlueprintExecutor.ActionResult(status, reasonCode, result.message(), null, null,
            result.taskId(), result.status().name(), acceptedAtTick);
    }
}
