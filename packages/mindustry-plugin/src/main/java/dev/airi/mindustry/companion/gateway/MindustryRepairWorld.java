package dev.airi.mindustry.companion.gateway;

import mindustry.Vars;
import mindustry.entities.units.AIController;
import mindustry.entities.units.UnitController;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

import java.util.HashMap;
import java.util.Map;

/**
 * Game-thread {@link RepairWorld} adapter for a host-owned Mindustry world.
 *
 * <p>It intentionally declines client-only multiplayer control. The host owns
 * unit state and Mindustry distributes that state to connected clients. The
 * adapter uses normal unit movement and health APIs; it does not emulate
 * pathfinding or mutate a remote client's world.</p>
 */
public final class MindustryRepairWorld implements RepairWorld {
    private final Map<String, ControlledPoly> controlled = new HashMap<>();

    @Override
    public boolean isHostAuthoritative() {
        return Vars.state != null && Vars.state.isGame() && (Vars.net == null || !Vars.net.client());
    }

    @Override
    public boolean isEligiblePoly(final int polyId) {
        final Unit unit = Groups.unit.getByID(polyId);
        return unit != null && unit.isValid() && unit.type != null && "poly".equals(unit.type.name)
            && !controlled.values().stream().anyMatch(entry -> entry.unit == unit);
    }

    @Override
    public boolean isValidFriendlyDamagedTarget(final int targetId) {
        final Building building = Groups.build.getByID(targetId);
        return building != null && building.isValid() && !building.dead && building.damaged();
    }

    @Override
    public boolean hasReachablePath(final int polyId, final int targetId) {
        // Mindustry's unit controller owns route selection. Do not replace it
        // with custom pathfinding; a controller failure becomes UNREACHABLE.
        return isEligiblePoly(polyId) && isValidFriendlyDamagedTarget(targetId);
    }

    @Override
    public void takeGatewayControl(final int polyId, final String taskId) {
        final Unit unit = Groups.unit.getByID(polyId);
        final Building target = Groups.build.getByID(findTargetIdForTask(taskId));
        if (unit == null || target == null) return;
        final UnitController previous = unit.controller();
        final AiriRepairController controller = new AiriRepairController(target);
        controlled.put(taskId, new ControlledPoly(unit, previous, controller));
        unit.controller(controller);
    }

    /** Associates the target before {@link #takeGatewayControl}. */
    @Override
    public void prepareRepair(final int polyId, final int targetId, final String taskId) {
        targetIds.put(taskId, targetId);
    }

    private final Map<String, Integer> targetIds = new HashMap<>();
    private int findTargetIdForTask(final String taskId) {
        return targetIds.getOrDefault(taskId, -1);
    }

    @Override
    public void releaseGatewayControl(final int polyId, final String taskId) {
        final ControlledPoly entry = controlled.remove(taskId);
        targetIds.remove(taskId);
        if (entry != null && entry.unit.isValid() && entry.unit.controller() == entry.controller) {
            entry.unit.controller(entry.previous);
        }
    }

    @Override
    public boolean wasRepurposedByPlayer(final int polyId, final String taskId) {
        final ControlledPoly entry = controlled.get(taskId);
        return entry == null || !entry.unit.isValid() || entry.unit.controller() != entry.controller;
    }

    @Override
    public RepairProgress advanceRepair(final int polyId, final int targetId, final String taskId) {
        final ControlledPoly entry = controlled.get(taskId);
        final Building target = Groups.build.getByID(targetId);
        if (entry == null || !entry.unit.isValid()) return RepairProgress.UNIT_UNAVAILABLE;
        if (target == null || !target.isValid() || target.dead || target.team != entry.unit.team) return RepairProgress.INVALID_TARGET;
        if (!target.damaged()) return RepairProgress.COMPLETE;
        return entry.controller.progress;
    }

    private record ControlledPoly(Unit unit, UnitController previous, AiriRepairController controller) { }

    private static final class AiriRepairController extends AIController {
        private final Building target;
        private RepairProgress progress = RepairProgress.DISPATCHING;

        private AiriRepairController(final Building target) {
            this.target = target;
        }

        @Override
        public void updateMovement() {
            if (unit == null || !unit.isValid() || target == null || !target.isValid() || target.dead) {
                progress = RepairProgress.INVALID_TARGET;
                return;
            }
            if (!target.damaged()) {
                progress = RepairProgress.COMPLETE;
                return;
            }
            final float repairRange = Math.max(24f, unit.type.buildRange);
            if (!unit.within(target, repairRange)) {
                progress = RepairProgress.TRAVELLING;
                moveTo(target, repairRange * 0.8f);
                return;
            }
            progress = RepairProgress.REPAIRING;
            // Poly construction speed is the game's authoritative repair rate.
            target.heal(Math.max(0.01f, unit.type.buildSpeed));
        }
    }
}
