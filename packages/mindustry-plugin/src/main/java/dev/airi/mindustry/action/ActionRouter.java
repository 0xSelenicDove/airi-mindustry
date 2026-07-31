package dev.airi.mindustry.action;

import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;

/** Parses the narrow action allowlist and dispatches it on Mindustry's update thread. */
public final class ActionRouter {
    private final BlueprintExecutor blueprintExecutor = new BlueprintExecutor();

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
        if (!"PLACE_BLUEPRINT".equals(type))
            return BlueprintExecutor.ActionResult.rejected("ACTION_NOT_ALLOWED", "Only PLACE_BLUEPRINT is enabled in this milestone.");
        final String refSnapshotId = value.getString("refSnapshotId", "");
        final long refTick = value.getLong("refTick", -1);
        final ActionValidator.ValidationResult freshness = ActionValidator.validateSnapshotReference((long) Vars.state.tick, refSnapshotId, refTick);
        if (!freshness.accepted())
            return BlueprintExecutor.ActionResult.rejected(freshness.status(), freshness.message());

        final JsonValue target = value.get("target");
        final JsonValue payload = value.get("payload");
        if (target == null || payload == null || !target.isObject() || !payload.isObject())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "PLACE_BLUEPRINT requires target and payload objects.");
        final String zoneId = target.getString("zoneId", "");
        if (zoneId.isBlank())
            return BlueprintExecutor.ActionResult.rejected("INVALID_ACTION", "PLACE_BLUEPRINT target requires a coarse zoneId.");

        return blueprintExecutor.place(
            payload.getString("schematicId", ""),
            Vars.state.rules.defaultTeam,
            zoneId,
            value.getBoolean("confirmed", false)
        );
    }
}
