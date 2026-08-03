package dev.airi.mindustry.companion.gateway;

/**
 * Narrow game-thread adapter used by the repair gateway.
 *
 * <p>The production implementation is responsible for mapping these calls to
 * Mindustry's host-side unit/building APIs. Keeping this boundary small makes
 * authority and player-override behaviour testable without an HTTP or LLM
 * dependency.</p>
 */
public interface RepairWorld {
    boolean isHostAuthoritative();
    boolean isEligiblePoly(int polyId);
    boolean isValidFriendlyDamagedTarget(int targetId);
    boolean hasReachablePath(int polyId, int targetId);
    /** Records task-local target state before the gateway takes unit control. */
    default void prepareRepair(int polyId, int targetId, String taskId) { }
    void takeGatewayControl(int polyId, String taskId);
    void releaseGatewayControl(int polyId, String taskId);
    boolean wasRepurposedByPlayer(int polyId, String taskId);
    RepairProgress advanceRepair(int polyId, int targetId, String taskId);
}
