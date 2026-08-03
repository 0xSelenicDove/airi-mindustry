package dev.airi.mindustry.companion.gateway;

import dev.airi.mindustry.companion.CompanionTaskStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Host-authoritative lifecycle for a single delegated Poly repair task.
 * All calls must originate from the game thread; this class deliberately has
 * no client-side command fallback.
 */
public final class HostRepairGateway {
    private final RepairWorld world;
    private final Map<String, Task> tasks = new HashMap<>();
    private long nextTaskId = 1;

    public HostRepairGateway(final RepairWorld world) {
        this.world = world;
    }

    public RepairGatewayResult submit(final RepairZoneCommand command) {
        if (!world.isHostAuthoritative()) {
            return result(null, CompanionTaskStatus.UNSUPPORTED_SESSION,
                "Repair delegation requires the authoritative host.");
        }
        if (!command.confirmed()) {
            return result(null, CompanionTaskStatus.CONFIRMATION_REQUIRED,
                "Confirm repair delegation before dispatching a Poly.");
        }
        if (!world.isEligiblePoly(command.polyId())) {
            return result(null, CompanionTaskStatus.UNIT_UNAVAILABLE, "The selected Poly is unavailable.");
        }
        if (!world.isValidFriendlyDamagedTarget(command.targetId())) {
            return result(null, CompanionTaskStatus.INVALID_TARGET, "The selected building is not a damaged friendly target.");
        }
        if (!world.hasReachablePath(command.polyId(), command.targetId())) {
            return result(null, CompanionTaskStatus.UNREACHABLE, "The selected Poly cannot reach that building.");
        }

        final String taskId = "repair-" + nextTaskId++;
        final Task task = new Task(taskId, command.polyId(), command.targetId(), CompanionTaskStatus.DISPATCHING);
        tasks.put(taskId, task);
        world.prepareRepair(task.polyId, task.targetId, task.id);
        world.takeGatewayControl(task.polyId, task.id);
        return result(task.id, task.status, "Repair approved; dispatching Poly to the damaged building.");
    }

    public RepairGatewayResult cancel(final String taskId, final long currentTick) {
        final Task task = tasks.get(taskId);
        if (task == null) return result(taskId, CompanionTaskStatus.INVALID_TARGET, "Repair task does not exist.");
        if (!terminal(task.status)) {
            task.status = CompanionTaskStatus.CANCELLED_BY_PLAYER;
            world.releaseGatewayControl(task.polyId, task.id);
        }
        return result(task.id, task.status, "Repair task cancelled by player.");
    }

    public RepairGatewayResult status(final String taskId) {
        final Task task = tasks.get(taskId);
        if (task == null) return result(taskId, CompanionTaskStatus.INVALID_TARGET, "Repair task does not exist.");
        advance(task);
        return result(task.id, task.status, messageFor(task.status));
    }

    private void advance(final Task task) {
        if (terminal(task.status)) return;
        if (world.wasRepurposedByPlayer(task.polyId, task.id)) {
            task.status = CompanionTaskStatus.CANCELLED_BY_PLAYER;
            world.releaseGatewayControl(task.polyId, task.id);
            return;
        }
        task.status = map(world.advanceRepair(task.polyId, task.targetId, task.id));
        if (terminal(task.status)) world.releaseGatewayControl(task.polyId, task.id);
    }

    private static CompanionTaskStatus map(final RepairProgress progress) {
        return switch (progress) {
            case DISPATCHING -> CompanionTaskStatus.DISPATCHING;
            case TRAVELLING -> CompanionTaskStatus.TRAVELLING;
            case REPAIRING -> CompanionTaskStatus.REPAIRING;
            case COMPLETE -> CompanionTaskStatus.COMPLETE;
            case INVALID_TARGET -> CompanionTaskStatus.INVALID_TARGET;
            case UNIT_UNAVAILABLE -> CompanionTaskStatus.UNIT_UNAVAILABLE;
            case UNREACHABLE -> CompanionTaskStatus.UNREACHABLE;
        };
    }

    private static boolean terminal(final CompanionTaskStatus status) {
        return status == CompanionTaskStatus.COMPLETE || status == CompanionTaskStatus.CANCELLED_BY_PLAYER
            || status == CompanionTaskStatus.INVALID_TARGET || status == CompanionTaskStatus.UNIT_UNAVAILABLE
            || status == CompanionTaskStatus.UNREACHABLE;
    }

    private static String messageFor(final CompanionTaskStatus status) {
        return switch (status) {
            case DISPATCHING -> "Repair approved; dispatching Poly.";
            case TRAVELLING -> "Poly is travelling to the repair target.";
            case REPAIRING -> "Poly is repairing the target.";
            case COMPLETE -> "Repair is complete.";
            case CANCELLED_BY_PLAYER -> "Repair task cancelled by player.";
            case INVALID_TARGET -> "Repair target is no longer valid.";
            case UNIT_UNAVAILABLE -> "Poly is no longer available.";
            case UNREACHABLE -> "Poly cannot reach the repair target.";
            default -> "Repair task status: " + status + ".";
        };
    }

    private static RepairGatewayResult result(final String taskId, final CompanionTaskStatus status, final String message) {
        return new RepairGatewayResult(taskId, status, message);
    }

    private static final class Task {
        private final String id;
        private final int polyId;
        private final int targetId;
        private CompanionTaskStatus status;

        private Task(final String id, final int polyId, final int targetId, final CompanionTaskStatus status) {
            this.id = id;
            this.polyId = polyId;
            this.targetId = targetId;
            this.status = status;
        }
    }
}
