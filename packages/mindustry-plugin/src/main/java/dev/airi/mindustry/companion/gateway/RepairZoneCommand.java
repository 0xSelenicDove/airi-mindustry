package dev.airi.mindustry.companion.gateway;

/** Confirmed player request to delegate one damaged building to one Poly. */
public record RepairZoneCommand(int polyId, int targetId, long refTick, boolean confirmed) {
    public static RepairZoneCommand confirmed(final int polyId, final int targetId, final long refTick) {
        return new RepairZoneCommand(polyId, targetId, refTick, true);
    }
}
