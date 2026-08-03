package dev.airi.mindustry.action;

import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Build;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes the one reviewed schematic available in this milestone. */
public final class BlueprintExecutor {
    private static final String STARTER_DUO_DEFENSE_ID = "starter-duo-defense";
    private static final Pattern ZONE_ID = Pattern.compile("^zone-(-?\\d+)-(-?\\d+)$");
    // Generated from the pinned v159 content by tools/generate-game-data.
    private static final String STARTER_DUO_DEFENSE_BASE64 = "bXNjaAF4nCWLUQqAIBBEJzWLOktH6QTRh9X+SSumRUR3Tw129w2PHTSoBbqV90B7GI2DeF5oaxayB8Q0K/QrO0d+uIy1kFtkaM8xkEebaifd7AG0aaHyET/UD51RQSadRmZVckZ5+ABIpRR+";

    /**
     * Resolves placement on the game thread. The bridge supplies a coarse zone,
     * never an assumed free map coordinate: this keeps map inspection local and
     * ensures the same Mindustry placement rules gate both preview and execution.
     */
    public ActionResult place(String schematicId, Team team, String zoneId, boolean confirmed) {
        if (!STARTER_DUO_DEFENSE_ID.equals(schematicId))
            return ActionResult.rejected("UNKNOWN_BLUEPRINT", "Only the reviewed starter-duo-defense blueprint is available.");
        if (Vars.net.client())
            return ActionResult.rejected("UNSUPPORTED_SESSION", "Blueprint placement is enabled only for local single-player games.");

        final Schematic schematic;
        try {
            schematic = Schematics.readBase64(STARTER_DUO_DEFENSE_BASE64);
        }
        catch (RuntimeException error) {
            return ActionResult.rejected("BLUEPRINT_INVALID", "The reviewed blueprint could not be loaded.");
        }

        // The local player may not be on rules.defaultTeam in a custom game.
        // Resolve both the inventory and fallback anchor from that actual team.
        final Team playerTeam = Vars.player == null ? team : Vars.player.team();
        final CoreBuild core = Vars.state.teams.get(playerTeam).core();
        if (core == null)
            return ActionResult.rejected("NO_CORE", "The player team has no core inventory.");
        final ObjectIntMap<Item> required = requirements(schematic);
        for (ObjectIntMap.Entry<Item> entry : required.entries()) {
            if (core.items.get(entry.key) < entry.value)
                return ActionResult.rejected("INSUFFICIENT_ITEMS", "Not enough " + entry.key.name + ": need " + entry.value + ".");
        }

        final ResolvedPlacement placement = findPlacement(schematic, playerTeam, zoneId, core);
        if (placement == null)
            return ActionResult.rejected("INVALID_PLACEMENT", "No valid starter-duo-defense anchor was found in the requested zone or around the player core.");
        final Seq<BuildPlan> plans = placement.plans();
        if (plans.size != schematic.tiles.size)
            return ActionResult.rejected("BLUEPRINT_UNAVAILABLE", "One or more blueprint blocks are unavailable or locked.");
        if (!confirmed)
            return ActionResult.confirmationRequired(
                "Blueprint preflight passed " + placement.locationDescription() + " at tile (" + placement.x() + ", " + placement.y() + "). Confirm to spend resources and place the structure.",
                placement.x(), placement.y()
            );

        // Preconditions above guarantee no replacement. Schematics.place executes on this update thread.
        Schematics.place(schematic, placement.x(), placement.y(), playerTeam, false);
        for (ObjectIntMap.Entry<Item> entry : required.entries())
            core.items.remove(entry.key, entry.value);
        return ActionResult.accepted("Placed starter-duo-defense and spent the required resources.", placement.x(), placement.y());
    }

    /**
     * Scan only the requested aggregate zone first. If it is entirely unsuitable
     * (as on Ancient Caldera), try one equally bounded area around the player's core.
     */
    private static ResolvedPlacement findPlacement(Schematic schematic, Team team, String zoneId, CoreBuild core) {
        final int[] origin = parseZoneOrigin(zoneId);
        if (origin != null) {
            final ResolvedPlacement inZone = scanRegion(schematic, team, origin[0], origin[1], origin[0] + 31, origin[1] + 31, false);
            if (inZone != null)
                return inZone;
        }

        // Keep the fallback local and bounded: 16 tiles in each direction from core.
        return scanRegion(schematic, team, core.tile.x - 16, core.tile.y - 16, core.tile.x + 15, core.tile.y + 15, true);
    }

    private static ResolvedPlacement scanRegion(Schematic schematic, Team team, int minX, int minY, int maxX, int maxY, boolean coreFallback) {
        final int startX = Math.max(0, minX);
        final int startY = Math.max(0, minY);
        final int endX = Math.min(Vars.world.width() - 1, maxX);
        final int endY = Math.min(Vars.world.height() - 1, maxY);
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                final Seq<BuildPlan> plans = Vars.schematics.toPlans(schematic, x, y);
                if (plans.size != schematic.tiles.size)
                    continue;
                boolean valid = true;
                for (BuildPlan plan : plans) {
                    // Build.validPlace verifies the target tile's floor/terrain,
                    // occupancy, footprint, and team rules. BuildPlan.placeable
                    // additionally preserves schematic-specific placement checks.
                    if (!Build.validPlace(plan.block, team, plan.x, plan.y, plan.rotation) || !plan.placeable(team)) {
                        valid = false;
                        break;
                    }
                }
                if (valid)
                    return new ResolvedPlacement(x, y, plans, coreFallback);
            }
        }
        return null;
    }

    private static int[] parseZoneOrigin(String zoneId) {
        if (zoneId == null)
            return null;
        final Matcher match = ZONE_ID.matcher(zoneId);
        if (!match.matches())
            return null;
        try {
            return new int[] { Integer.parseInt(match.group(1)) * 32, Integer.parseInt(match.group(2)) * 32 };
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ObjectIntMap<Item> requirements(Schematic schematic) {
        final ObjectIntMap<Item> required = new ObjectIntMap<>();
        for (Schematic.Stile tile : schematic.tiles) {
            for (ItemStack stack : tile.block.requirements)
                required.increment(stack.item, stack.amount);
        }
        return required;
    }

    private record ResolvedPlacement(int x, int y, Seq<BuildPlan> plans, boolean coreFallback) {
        String locationDescription() {
            return coreFallback ? "near the player core (requested zone had no valid anchor)" : "in the requested zone";
        }
    }

    public record ActionResult(String status, String reasonCode, String message, Integer targetX, Integer targetY,
                               String taskId, String taskStatus, Long acceptedAtTick) {
        public ActionResult(String status, String reasonCode, String message, Integer targetX, Integer targetY) {
            this(status, reasonCode, message, targetX, targetY, null, null, null);
        }
        static ActionResult accepted(String message, int x, int y) { return new ActionResult("accepted", "ACCEPTED", message, x, y); }
        static ActionResult rejected(String reasonCode, String message) { return new ActionResult("rejected", reasonCode, message, null, null); }
        static ActionResult confirmationRequired(String message, int x, int y) { return new ActionResult("confirmation-required", "CONFIRMATION_REQUIRED", message, x, y); }
    }
}
