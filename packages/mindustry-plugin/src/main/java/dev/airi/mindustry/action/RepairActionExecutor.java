package dev.airi.mindustry.action;

/** Game-thread boundary for REPAIR_ZONE execution. */
@FunctionalInterface
public interface RepairActionExecutor {
    BlueprintExecutor.ActionResult submit(int polyId, int buildingId, long refTick, boolean confirmed, String idempotencyKey);

    /** Called from Mindustry's update thread to project physical task progress. */
    default void refresh(final long currentTick) { }
}
