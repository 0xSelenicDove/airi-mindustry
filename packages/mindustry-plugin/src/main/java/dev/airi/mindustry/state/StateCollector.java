package dev.airi.mindustry.state;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.util.Map;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.TreeMap;

/** Captures a compact, versioned game snapshot on Mindustry's update thread. */
public final class StateCollector {
    private static final int LOW_RESOURCE_THRESHOLD = 100;
    private final ZoneAnalyzer zoneAnalyzer = new ZoneAnalyzer();
    private long snapshotSequence;

    public String collect() {
        if (!Vars.state.isGame())
            return inactiveStateJson();

        final Team playerTeam = Vars.state.rules.defaultTeam;
        final long gameTick = (long) Vars.state.tick;
        final String snapshotId = "s-" + (++snapshotSequence) + "-t" + gameTick;
        final CoreBuild core = playerTeam.core();
        final PowerMetrics power = powerMetrics(playerTeam);
        final Seq<ZoneAnalyzer.ZoneSummary> zones = zoneAnalyzer.analyze(playerTeam);

        return String.format(Locale.ROOT,
            "{\"schemaVersion\":2,\"snapshotId\":\"%s\",\"gameTick\":%d,\"gameRunning\":true,\"mapName\":%s,\"wave\":{\"number\":%d,\"timeRemainingTicks\":%.2f},\"players\":{\"count\":%d},\"units\":{\"friendly\":%d,\"enemy\":%d},\"core\":%s,\"power\":%s,\"factories\":%s,\"resources\":%s,\"zones\":%s,\"alerts\":%s}",
            snapshotId,
            gameTick,
            mapNameJson(),
            Vars.state.wave,
            Vars.state.wavetime,
            Groups.player.size(),
            countFriendlyUnits(playerTeam),
            countEnemyUnits(playerTeam),
            coreJson(core),
            power.json(),
            factoriesJson(playerTeam),
            criticalResourcesJson(core),
            zonesJson(zones),
            alertsJson(core, power, zones)
        );
    }

    public static String inactiveStateJson() {
        return "{\"schemaVersion\":2,\"snapshotId\":\"inactive\",\"gameTick\":0,\"gameRunning\":false,\"mapName\":null,\"wave\":{\"number\":0,\"timeRemainingTicks\":0},\"players\":{\"count\":0},\"units\":{\"friendly\":0,\"enemy\":0},\"core\":null,\"power\":{\"networkCount\":0,\"produced\":0,\"needed\":0,\"stored\":0,\"capacity\":0},\"factories\":[],\"resources\":{\"critical\":[]},\"zones\":[],\"alerts\":[]}";
    }

    private static int countFriendlyUnits(Team playerTeam) {
        final int[] count = { 0 };
        Groups.unit.each(unit -> {
            if (unit.team == playerTeam)
                count[0]++;
        });
        return count[0];
    }

    private static int countEnemyUnits(Team playerTeam) {
        final int[] count = { 0 };
        Groups.unit.each(unit -> {
            if (unit.team != playerTeam)
                count[0]++;
        });
        return count[0];
    }

    private static String mapNameJson() {
        return Vars.state.map == null ? "null" : "\"" + escapeJson(Vars.state.map.plainName()) + "\"";
    }

    private static String coreJson(CoreBuild core) {
        if (core == null)
            return "null";

        final StringJoiner inventory = new StringJoiner(",", "[", "]");
        for (Item item : Vars.content.items()) {
            final int amount = core.items.get(item);
            if (amount > 0)
                inventory.add(String.format(Locale.ROOT, "{\"name\":\"%s\",\"amount\":%d}", escapeJson(item.name), amount));
        }
        return String.format(Locale.ROOT, "{\"health\":%.2f,\"maxHealth\":%.2f,\"inventory\":%s}", core.health, core.maxHealth, inventory);
    }

    private static PowerMetrics powerMetrics(Team playerTeam) {
        final ObjectSet<PowerGraph> graphs = new ObjectSet<>();
        Groups.build.each(build -> {
            if (build.team == playerTeam && build.block.hasPower && build.power != null)
                graphs.add(build.power.graph);
        });
        float produced = 0f, needed = 0f, stored = 0f, capacity = 0f;
        for (PowerGraph graph : graphs) {
            produced += graph.getLastPowerProduced();
            needed += graph.getLastPowerNeeded();
            stored += graph.getLastPowerStored();
            capacity += graph.getLastCapacity();
        }
        return new PowerMetrics(graphs.size, produced, needed, stored, capacity);
    }

    private static String factoriesJson(Team playerTeam) {
        final Map<String, Integer> counts = new TreeMap<>();
        Groups.build.each(build -> {
            if (build.team == playerTeam && (build.block.category == Category.production || build.block.category == Category.crafting))
                counts.merge(build.block.name, 1, Integer::sum);
        });
        final StringJoiner factories = new StringJoiner(",", "[", "]");
        counts.forEach((name, count) -> factories.add(String.format(Locale.ROOT, "{\"name\":\"%s\",\"count\":%d}", escapeJson(name), count)));
        return factories.toString();
    }

    private static String criticalResourcesJson(CoreBuild core) {
        final StringJoiner resources = new StringJoiner(",", "{\"critical\":[", "]}");
        if (core == null)
            return resources.toString();
        for (Item item : Vars.content.items()) {
            final int amount = core.items.get(item);
            if (amount <= LOW_RESOURCE_THRESHOLD)
                resources.add(String.format(Locale.ROOT, "{\"item\":\"%s\",\"amount\":%d,\"threshold\":%d}", escapeJson(item.name), amount, LOW_RESOURCE_THRESHOLD));
        }
        return resources.toString();
    }

    private static String zonesJson(Seq<ZoneAnalyzer.ZoneSummary> zones) {
        final StringJoiner result = new StringJoiner(",", "[", "]");
        for (ZoneAnalyzer.ZoneSummary zone : zones) {
            result.add(String.format(Locale.ROOT, "{\"id\":\"%s\",\"role\":\"%s\",\"buildingCount\":%d,\"defenseCount\":%d,\"productionCount\":%d,\"damagedBuildingCount\":%d,\"enemyUnitCount\":%d,\"priority\":%d}",
                zone.id(), zone.role(), zone.buildingCount(), zone.defenseCount(), zone.productionCount(), zone.damagedBuildingCount(), zone.enemyUnitCount(), zone.priority()));
        }
        return result.toString();
    }

    private static String alertsJson(CoreBuild core, PowerMetrics power, Seq<ZoneAnalyzer.ZoneSummary> zones) {
        final StringJoiner alerts = new StringJoiner(",", "[", "]");
        if (power.needed > 0f && power.produced < power.needed)
            alerts.add("{\"kind\":\"power-shortage\",\"severity\":\"critical\",\"message\":\"Power production is below demand.\"}");
        if (core != null && core.maxHealth > 0f && core.health / core.maxHealth < 0.35f)
            alerts.add("{\"kind\":\"core-health-low\",\"severity\":\"critical\",\"message\":\"Core integrity is below 35%.\"}");
        for (ZoneAnalyzer.ZoneSummary zone : zones) {
            if (zone.enemyUnitCount() > 0 && zone.defenseCount() == 0 && (zone.productionCount() > 0 || zone.role().equals("core")))
                alerts.add("{\"kind\":\"defense-exposed\",\"severity\":\"warning\",\"zoneId\":\"" + zone.id() + "\",\"message\":\"Enemies are present in an important zone with no local turret coverage.\"}");
            if (zone.damagedBuildingCount() > 0)
                alerts.add("{\"kind\":\"buildings-damaged\",\"severity\":\"warning\",\"zoneId\":\"" + zone.id() + "\",\"message\":\"" + zone.damagedBuildingCount() + " building" + (zone.damagedBuildingCount() == 1 ? " is" : "s are") + " damaged in this zone.\"}");
        }
        return alerts.toString();
    }

    private record PowerMetrics(int networkCount, float produced, float needed, float stored, float capacity) {
        private String json() {
            return String.format(Locale.ROOT, "{\"networkCount\":%d,\"produced\":%.2f,\"needed\":%.2f,\"stored\":%.2f,\"capacity\":%.2f}", networkCount, produced, needed, stored, capacity);
        }
    }

    private static String escapeJson(String value) {
        final StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20)
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else
                        escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }
}
