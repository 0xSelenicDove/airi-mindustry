package dev.airi.mindustry.action;

import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import dev.airi.mindustry.action.safety.ActionSafetyGateway;

import java.util.function.LongSupplier;
import java.util.HashMap;
import java.util.Map;

/** Parses the narrow action allowlist and dispatches it on Mindustry's update thread. */
public final class ActionRouter {
    private final BlueprintExecutor blueprintExecutor = new BlueprintExecutor();
    private final LongSupplier currentTick;
    private final RepairActionExecutor repairExecutor;
    private final Map<String, BlueprintExecutor.ActionResult> idempotentResults = new HashMap<>();

    public ActionRouter() {
        this(() -> (long) Vars.state.tick, new MindustryRepairActionExecutor());
    }

    /** Visible for deterministic action-contract tests; production uses the default constructor. */
    public ActionRouter(final LongSupplier currentTick, final RepairActionExecutor repairExecutor) {
        this.currentTick = currentTick;
        this.repairExecutor = repairExecutor;
    }

    public BlueprintExecutor.ActionResult submit(String body) {
        final JsonValue value;
        try {
            value = new JsonReader().parse(body);
        }
        catch (RuntimeException error) {
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "Request body must be valid JSON.");
        }
        if (!value.isObject())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "Action request must be a JSON object.");

        final String type = value.getString("type", "");
        final String refSnapshotId = value.getString("refSnapshotId", "");
        final long refTick = value.getLong("refTick", -1);
        final ActionValidator.ValidationResult freshness = ActionValidator.validateSnapshotReference(currentTick.getAsLong(), refSnapshotId, refTick);
        if (!freshness.accepted())
            return BlueprintExecutor.ActionResult.rejected(freshness.status(), freshness.message());

        final String idempotencyKey = value.getString("idempotencyKey", "");
        if (!idempotencyKey.isBlank() && idempotentResults.containsKey(idempotencyKey)) return idempotentResults.get(idempotencyKey);

        if ("REPAIR_ZONE".equals(type)) return submitRepair(value, refTick);
        if (!"PLACE_BLUEPRINT".equals(type))
            return BlueprintExecutor.ActionResult.rejected("ACTION_NOT_ALLOWED", "Only PLACE_BLUEPRINT and REPAIR_ZONE are enabled.");

        final JsonValue target = value.get("target");
        final JsonValue payload = value.get("payload");
        if (target == null || payload == null || !target.isObject() || !payload.isObject())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "PLACE_BLUEPRINT requires target and payload objects.");
        final String zoneId = target.getString("zoneId", "");
        if (zoneId.isBlank())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "PLACE_BLUEPRINT target requires a coarse zoneId.");

        final BlueprintExecutor.ActionResult permitted = authorizeConfirmedMutation(value, type, refTick);
        if (permitted != null) return permitted;
        final BlueprintExecutor.ActionResult result = blueprintExecutor.place(
            payload.getString("schematicId", ""),
            Vars.state.rules.defaultTeam,
            zoneId,
            value.getBoolean("confirmed", false)
        );
        return remember(idempotencyKey, result);
    }

    /** Called on the update thread so physical Poly progress reaches the bridge without HTTP polling. */
    public void refreshRepairStatuses(final long currentTick) {
        repairExecutor.refresh(currentTick);
    }

    private BlueprintExecutor.ActionResult submitRepair(final JsonValue value, final long refTick) {
        final JsonValue target = value.get("target");
        final JsonValue payload = value.get("payload");
        if (target == null || payload == null || !target.isObject() || !payload.isObject())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "REPAIR_ZONE requires target and payload objects.");
        final int buildingId = target.getInt("buildingId", -1);
        final int polyId = payload.getInt("polyId", -1);
        if (buildingId < 0)
            return BlueprintExecutor.ActionResult.rejected("INVALID_TARGET", "REPAIR_ZONE target requires a buildingId.");
        if (polyId < 0)
            return BlueprintExecutor.ActionResult.rejected("UNIT_UNAVAILABLE", "REPAIR_ZONE payload requires an eligible Poly.");
        final BlueprintExecutor.ActionResult permitted = authorizeConfirmedMutation(value, "REPAIR_ZONE", refTick);
        if (permitted != null) return permitted;
        final String idempotencyKey = value.getString("idempotencyKey", "");
        return remember(idempotencyKey, repairExecutor.submit(polyId, buildingId, refTick, value.getBoolean("confirmed", false), idempotencyKey));
    }

    /** Applies the global tick-based guard only to confirmed world mutations. */
    private BlueprintExecutor.ActionResult authorizeConfirmedMutation(final JsonValue value, final String type, final long refTick) {
        if (!value.getBoolean("confirmed", false)) return null;
        ActionSafetyGateway.ensureLiveSession(Vars.net == null || !Vars.net.client());
        final String key = value.getString("idempotencyKey", "");
        final String actionId = key.isBlank() ? type.toLowerCase() + "-" + refTick : key;
        final ActionSafetyGateway.Reply reply = ActionSafetyGateway.reserve(actionId, type, refTick, key);
        if ("RATE_LIMITED".equals(reply.reasonCode()))
            return BlueprintExecutor.ActionResult.rejected("RATE_LIMITED", "Action limit reached for this game tick.");
        if ("UNSUPPORTED_SESSION".equals(reply.reasonCode()))
            return BlueprintExecutor.ActionResult.rejected("UNSUPPORTED_SESSION", "World-changing actions require the authoritative host.");
        if (!reply.mutationPerformed())
            return new BlueprintExecutor.ActionResult("accepted", "IDEMPOTENT", "Returning the original action result.", null, null,
                reply.taskId(), null, refTick);
        return null;
    }

    private BlueprintExecutor.ActionResult remember(final String idempotencyKey, final BlueprintExecutor.ActionResult result) {
        if (!idempotencyKey.isBlank() && !"rejected".equals(result.status()) && !"confirmation-required".equals(result.status()))
            idempotentResults.put(idempotencyKey, result);
        return result;
    }
}
