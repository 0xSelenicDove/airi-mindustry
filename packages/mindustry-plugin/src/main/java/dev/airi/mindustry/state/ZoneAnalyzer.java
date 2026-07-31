package dev.airi.mindustry.state;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Category;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Converts the local world into bounded, coarse zone summaries. Raw tiles and
 * individual building positions deliberately never leave the game client.
 */
public final class ZoneAnalyzer {
    private static final int ZONE_SIZE_TILES = 32;
    private static final int MAX_PUBLISHED_ZONES = 12;

    public Seq<ZoneSummary> analyze(Team playerTeam) {
        final ObjectMap<String, MutableZone> zones = new ObjectMap<>();

        Groups.build.each(build -> {
            if (build.team != playerTeam)
                return;

            final MutableZone zone = zones.get(zoneId(build.tileX(), build.tileY()));
            final MutableZone target = zone == null
                ? addZone(zones, build.tileX(), build.tileY())
                : zone;
            target.addBuilding(build);
        });

        Groups.unit.each(unit -> {
            if (unit.team == playerTeam)
                return;

            final MutableZone zone = zones.get(zoneId(unit.tileX(), unit.tileY()));
            if (zone != null)
                zone.enemyUnitCount++;
        });

        final Seq<ZoneSummary> summaries = new Seq<>();
        for (MutableZone zone : zones.values()) {
            if (zone.isRelevant())
                summaries.add(zone.toSummary());
        }
        summaries.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        if (summaries.size > MAX_PUBLISHED_ZONES)
            summaries.truncate(MAX_PUBLISHED_ZONES);
        return summaries;
    }

    private static MutableZone addZone(ObjectMap<String, MutableZone> zones, int tileX, int tileY) {
        final int x = Math.floorDiv(tileX, ZONE_SIZE_TILES);
        final int y = Math.floorDiv(tileY, ZONE_SIZE_TILES);
        final MutableZone zone = new MutableZone("zone-" + x + "-" + y);
        zones.put(zone.id, zone);
        return zone;
    }

    private static String zoneId(int tileX, int tileY) {
        return "zone-" + Math.floorDiv(tileX, ZONE_SIZE_TILES) + "-" + Math.floorDiv(tileY, ZONE_SIZE_TILES);
    }

    /** A published aggregate; its id can be resolved locally by future action code. */
    public record ZoneSummary(
        String id,
        String role,
        int buildingCount,
        int defenseCount,
        int productionCount,
        int damagedBuildingCount,
        int enemyUnitCount,
        int priority
    ) {}

    private static final class MutableZone {
        private final String id;
        private int buildingCount;
        private int defenseCount;
        private int productionCount;
        private int coreCount;
        private int damagedBuildingCount;
        private int enemyUnitCount;

        private MutableZone(String id) {
            this.id = id;
        }

        private void addBuilding(Building build) {
            buildingCount++;
            if (build.block.category == Category.turret)
                defenseCount++;
            if (build.block.category == Category.production || build.block.category == Category.crafting)
                productionCount++;
            if (build.block instanceof CoreBlock)
                coreCount++;
            if (build.maxHealth > 0f && build.health < build.maxHealth)
                damagedBuildingCount++;
        }

        private boolean isRelevant() {
            return coreCount > 0 || defenseCount > 0 || productionCount > 0 || damagedBuildingCount > 0 || enemyUnitCount > 0;
        }

        private ZoneSummary toSummary() {
            final String role = coreCount > 0 ? "core"
                : defenseCount > 0 && productionCount > 0 ? "mixed"
                : defenseCount > 0 ? "defense"
                : productionCount > 0 ? "production"
                : "infrastructure";
            return new ZoneSummary(id, role, buildingCount, defenseCount, productionCount,
                damagedBuildingCount, enemyUnitCount, priority());
        }

        private int priority() {
            return coreCount * 1_000 + enemyUnitCount * 100 + damagedBuildingCount * 50
                + defenseCount * 10 + productionCount * 5;
        }
    }
}
