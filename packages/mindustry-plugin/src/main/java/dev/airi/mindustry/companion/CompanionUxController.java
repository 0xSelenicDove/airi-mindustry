package dev.airi.mindustry.companion;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic policy core for AIRI companion UX. A Mindustry adapter supplies
 * observations and later translates accepted outcomes into native Poly commands.
 */
public final class CompanionUxController {
    private static final int STARTER_DUO_COPPER_COST = 64;
    private static final int STARTER_DUO_SUGGESTION_COPPER = 70;
    private final Set<String> notifiedAlertKeys = new HashSet<>();

    public static CompanionUxController forTddAcceptanceTests() {
        return new CompanionUxController();
    }

    public CompanionOutcome handle(CompanionRequest request) {
        return switch (request.command()) {
            case ANSWER_EXPOSURE -> answerExposure(request.observation());
            case SUGGEST_BLUEPRINT -> suggestBlueprint(request.observation());
            case PREFLIGHT_BLUEPRINT -> preflightBlueprint(request.observation());
            case CONFIRM_BLUEPRINT -> confirmBlueprint(request.observation(), request.confirmed());
            case REPEAT_ALERT -> repeatAlert(request.observation());
            case ASSIGN_POLY -> assignPoly(request.observation());
            case PROPOSE_REPAIR -> proposeRepair(request.observation());
            case CONFIRM_REPAIR -> confirmRepair(request.observation(), request.confirmed());
            case OBSERVE_REPAIR -> observeRepair(request.observation());
            case RESUME_REPAIR -> resumeRepair(request.observation());
        };
    }

    private CompanionOutcome answerExposure(CompanionObservation o) {
        if (!o.bridgeAvailable()) return outcome(CompanionTaskStatus.NOT_NEEDED, "local match data is unavailable", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!o.snapshotFresh()) return outcome(CompanionTaskStatus.STALE_WORLD_STATE, "A fresh local state read is required before answering.", Set.of("get_mindustry_context", "get_mindustry_events"), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        final String message = o.defenseExposed() ? "Your base is exposed to enemies." : "No active exposure was detected in fresh local state.";
        return outcome(CompanionTaskStatus.IDLE, message, Set.of("get_mindustry_context", "get_mindustry_events"), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
    }

    private CompanionOutcome suggestBlueprint(CompanionObservation o) {
        if (!o.defenseExposed()) return outcome(CompanionTaskStatus.NOT_NEEDED, "No active defense exposure was detected.", Set.of(), null, null, null, 0, false, Map.of(), false, CompanionPriority.OPTIONAL_WORK, false);
        if (o.copper() < STARTER_DUO_SUGGESTION_COPPER) return outcome(CompanionTaskStatus.INSUFFICIENT_ITEMS, "You need " + (STARTER_DUO_SUGGESTION_COPPER - o.copper()) + " more copper.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        return outcome(CompanionTaskStatus.CONFIRMATION_REQUIRED, "Starter Duo Defense is available for confirmation.", Set.of(), new CompanionProposal("PLACE_BLUEPRINT", "starter-duo-defense", true), null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
    }

    private CompanionOutcome preflightBlueprint(CompanionObservation o) {
        if (!o.snapshotFresh()) return outcome(CompanionTaskStatus.STALE_WORLD_STATE, "World state is stale; refresh before placement.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        if (o.requestedZoneBuildable()) return outcome(CompanionTaskStatus.CONFIRMATION_REQUIRED, "Blueprint preflight passed in the requested zone.", Set.of(), new CompanionProposal("PLACE_BLUEPRINT", "starter-duo-defense", true), Optional.of(new CompanionTarget("requested-zone-anchor")), null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        if (o.coreFallbackBuildable()) return outcome(CompanionTaskStatus.CONFIRMATION_REQUIRED, "Blueprint preflight passed near the player core.", Set.of(), new CompanionProposal("PLACE_BLUEPRINT", "starter-duo-defense", true), Optional.of(new CompanionTarget("core-fallback-anchor")), null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        return outcome(CompanionTaskStatus.INVALID_PLACEMENT, "No valid blueprint anchor exists.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
    }

    private CompanionOutcome confirmBlueprint(CompanionObservation o, boolean confirmed) {
        final CompanionOutcome preview = preflightBlueprint(o);
        if (preview.taskStatus() != CompanionTaskStatus.CONFIRMATION_REQUIRED) return preview;
        if (!confirmed) return preview;
        if (o.copper() < STARTER_DUO_COPPER_COST) return outcome(CompanionTaskStatus.INSUFFICIENT_ITEMS, "Not enough copper for placement.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        return outcome(CompanionTaskStatus.COMPLETE, "Blueprint placement accepted.", Set.of(), null, preview.resolvedTarget(), null, 0, true, Map.of("copper", STARTER_DUO_COPPER_COST), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
    }

    private CompanionOutcome repeatAlert(CompanionObservation o) {
        final CompanionPriority priority = o.coreThreat() ? CompanionPriority.CORE_SURVIVAL : CompanionPriority.ACTIVE_DEFENSE_REPAIR;
        final String key = o.coreThreat() ? "core-threat" : "defense-exposed";
        final boolean notify = notifiedAlertKeys.add(key);
        return outcome(CompanionTaskStatus.IDLE, o.coreThreat() ? "Core threat requires attention." : "Defense exposure detected.", Set.of(), null, null, null, 0, false, Map.of(), notify, priority, false);
    }

    private CompanionOutcome assignPoly(CompanionObservation o) {
        if (!o.eligiblePoly()) return outcome(CompanionTaskStatus.UNIT_UNAVAILABLE, "There is no eligible Poly.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        return outcome(CompanionTaskStatus.IDLE, "Poly assigned and idle.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, true);
    }

    private CompanionOutcome proposeRepair(CompanionObservation o) {
        if (!o.targetValid() || !o.pathReachable()) return outcome(CompanionTaskStatus.INVALID_TARGET, "There is no repairable reachable target.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        return outcome(CompanionTaskStatus.CONFIRMATION_REQUIRED, "Repair proposal awaits confirmation.", Set.of(), new CompanionProposal("REPAIR_ZONE", null, true), null, new CompanionTarget(o.selectedTargetId()), Math.max(0, o.damagedTargetCount() - 1), false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
    }

    private CompanionOutcome confirmRepair(CompanionObservation o, boolean confirmed) {
        if (!o.hostAuthoritative()) return outcome(CompanionTaskStatus.UNSUPPORTED_SESSION, "A host-authoritative companion is required for shared-world repair.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (o.targetRestored()) return outcome(CompanionTaskStatus.NOT_NEEDED, "Repair target is already complete.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (o.targetDestroyed() || !o.targetValid()) return outcome(CompanionTaskStatus.INVALID_TARGET, "Target changed; select a new target only with renewed approval.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!o.resourcesAvailable()) return outcome(CompanionTaskStatus.INSUFFICIENT_ITEMS, "Repair paused: insufficient resources.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!o.polyAlive()) return outcome(CompanionTaskStatus.UNIT_UNAVAILABLE, "Assigned Poly is unavailable.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (o.polyBusy()) return outcome(CompanionTaskStatus.UNIT_BUSY, "Poly is busy; explicit player approval is required to interrupt it.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!o.pathReachable()) return outcome(CompanionTaskStatus.UNREACHABLE, "Repair target is unreachable.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!confirmed) return outcome(CompanionTaskStatus.CONFIRMATION_REQUIRED, "Repair proposal awaits confirmation.", Set.of(), new CompanionProposal("REPAIR_ZONE", null, true), null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        if (!o.gatewayAssignmentConfirmed()) return outcome(CompanionTaskStatus.DISPATCHING, "Repair approved; dispatching Poly.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, true);
        return outcome(CompanionTaskStatus.TRAVELLING, "Poly is travelling to the repair target.", Set.of(), null, null, null, 0, true, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, true);
    }

    private CompanionOutcome observeRepair(CompanionObservation o) {
        if (o.playerCancelled() || o.playerRepurposed()) return outcome(CompanionTaskStatus.CANCELLED_BY_PLAYER, "Repair task cancelled by player command.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (o.coreThreat()) return outcome(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, "Paused repair: Core defense is now priority.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.CORE_SURVIVAL, false);
        if (o.zoneDangerous()) return outcome(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, "Paused repair because the zone is dangerous.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.ACTIVE_DEFENSE_REPAIR, false);
        if (o.newWave()) return outcome(CompanionTaskStatus.PAUSED_FOR_EMERGENCY, "Paused optional repair for the new wave.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.DEFENSE_READINESS, false);
        if (!o.polyAlive()) return outcome(CompanionTaskStatus.UNIT_UNAVAILABLE, "Assigned Poly is unavailable.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (!o.pathReachable()) return outcome(CompanionTaskStatus.UNREACHABLE, "Repair target is unreachable.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        if (o.targetRestored()) return outcome(CompanionTaskStatus.COMPLETE, "Repair complete.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        return outcome(CompanionTaskStatus.REPAIRING, "Poly is repairing the target.", Set.of(), null, null, null, 0, false, Map.of(), false, CompanionPriority.ACTIVE_DEFENSE_REPAIR, true);
    }

    private CompanionOutcome resumeRepair(CompanionObservation o) {
        if (!o.persistedTaskValid() || o.staleUnitReference()) return outcome(CompanionTaskStatus.CLEARED_AFTER_REVALIDATION, "Saved repair task was cleared after revalidation.", Set.of(), null, null, null, 0, false, Map.of(), true, CompanionPriority.OPTIONAL_WORK, false);
        return outcome(CompanionTaskStatus.REPAIRING, "Repair task resumed after revalidation.", Set.of(), null, null, null, 0, false, Map.of(), false, CompanionPriority.ACTIVE_DEFENSE_REPAIR, true);
    }

    private static CompanionOutcome outcome(CompanionTaskStatus status, String message, Set<String> tools, CompanionProposal proposal,
        Optional<CompanionTarget> resolvedTarget, CompanionTarget repairTarget, int remaining, boolean mutation, Map<String, Integer> spent,
        boolean notify, CompanionPriority priority, boolean retained) {
        return new CompanionOutcome(status, message, tools, proposal, resolvedTarget == null ? Optional.empty() : resolvedTarget,
            repairTarget, remaining, mutation, spent, notify, priority, retained, false);
    }
}
