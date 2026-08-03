package dev.airi.mindustry.companion.gateway;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Deterministic host-world fixture for the gateway contract. It models the
 * game-thread authority, player override, travel, and repair progression; the
 * production adapter supplies the corresponding Mindustry calls in-game.
 */
final class MindustryRepairFixture implements AutoCloseable, RepairWorld {
    private final boolean hostAuthoritative;
    private final HostRepairGateway gateway = new HostRepairGateway(this);
    private final Map<Integer, UnitState> units = new HashMap<>();
    private final Map<Integer, TargetState> targets = new HashMap<>();
    private long tick;
    private int nextUnitId = 1;
    private int nextTargetId = 1;

    private MindustryRepairFixture(final boolean hostAuthoritative) {
        this.hostAuthoritative = hostAuthoritative;
    }

    static MindustryRepairFixture singlePlayerHost() {
        return new MindustryRepairFixture(true);
    }

    static MindustryRepairFixture multiplayerClient() {
        return new MindustryRepairFixture(false);
    }

    RepairTarget placeDamagedFriendlyWall(final int x, final int y, final float health) {
        final int id = nextTargetId++;
        targets.put(id, new TargetState(health, 100f));
        return new RepairTarget(id, 100f);
    }

    CompanionUnit spawnEligiblePoly(final int x, final int y) {
        final int id = nextUnitId++;
        units.put(id, new UnitState());
        return new CompanionUnit(id);
    }

    HostRepairGateway gateway() { return gateway; }
    long currentTick() { return tick; }
    float health(final RepairTarget target) { return targets.get(target.id()).health; }
    boolean isGatewayControlling(final CompanionUnit unit) { return units.get(unit.id()).taskId != null; }
    boolean canPlayerCommand(final CompanionUnit unit) { return !isGatewayControlling(unit); }
    boolean unitReceivedCommand(final CompanionUnit unit) { return units.get(unit.id()).receivedCommand; }

    void issuePlayerMoveOrder(final CompanionUnit unit, final int x, final int y) {
        final UnitState state = units.get(unit.id());
        state.playerRepurposed = true;
    }

    void tickUntil(final BooleanSupplier condition) {
        for (int attempts = 0; attempts < 100 && !condition.getAsBoolean(); attempts++) {
            tick++;
        }
        if (!condition.getAsBoolean()) throw new AssertionError("Condition was not reached in 100 ticks.");
    }

    @Override public boolean isHostAuthoritative() { return hostAuthoritative; }
    @Override public boolean isEligiblePoly(final int polyId) {
        final UnitState unit = units.get(polyId);
        return unit != null && unit.taskId == null && !unit.playerRepurposed;
    }
    @Override public boolean isValidFriendlyDamagedTarget(final int targetId) {
        final TargetState target = targets.get(targetId);
        return target != null && target.health < target.maxHealth;
    }
    @Override public boolean hasReachablePath(final int polyId, final int targetId) { return units.containsKey(polyId) && targets.containsKey(targetId); }
    @Override public void takeGatewayControl(final int polyId, final String taskId) {
        final UnitState unit = units.get(polyId);
        unit.taskId = taskId;
        unit.receivedCommand = true;
    }
    @Override public void releaseGatewayControl(final int polyId, final String taskId) {
        final UnitState unit = units.get(polyId);
        if (taskId.equals(unit.taskId)) unit.taskId = null;
    }
    @Override public boolean wasRepurposedByPlayer(final int polyId, final String taskId) {
        return units.get(polyId).playerRepurposed;
    }
    @Override public RepairProgress advanceRepair(final int polyId, final int targetId, final String taskId) {
        final UnitState unit = units.get(polyId);
        final TargetState target = targets.get(targetId);
        final long elapsed = tick - unit.dispatchTick;
        if (unit.dispatchTick == 0) {
            unit.dispatchTick = tick;
            return RepairProgress.DISPATCHING;
        }
        if (elapsed < 3) return RepairProgress.TRAVELLING;
        if (elapsed < 8) {
            target.health = Math.min(target.maxHealth, target.health + 20f);
            return RepairProgress.REPAIRING;
        }
        target.health = target.maxHealth;
        return RepairProgress.COMPLETE;
    }

    @Override public void close() { }

    private static final class UnitState {
        private String taskId;
        private boolean playerRepurposed;
        private boolean receivedCommand;
        private long dispatchTick;
    }
    private static final class TargetState {
        private float health;
        private final float maxHealth;
        private TargetState(final float health, final float maxHealth) { this.health = health; this.maxHealth = maxHealth; }
    }
}
