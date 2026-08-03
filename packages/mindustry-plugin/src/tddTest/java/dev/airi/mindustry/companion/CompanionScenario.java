package dev.airi.mindustry.companion;

/** Test-only fixtures. Production receives the same request/observation contract from its Mindustry adapter. */
final class CompanionScenario {
    private CompanionScenario() {}

    static CompanionRequest exposedBaseQuestion() { return request(CompanionCommand.ANSWER_EXPOSURE, o -> o.defenseExposed(true), false); }
    static CompanionRequest exposedBaseQuestionWithBridgeOffline() { return request(CompanionCommand.ANSWER_EXPOSURE, o -> o.bridgeAvailable(false), false); }
    static CompanionRequest noActiveThreatQuestion() { return request(CompanionCommand.ANSWER_EXPOSURE, o -> {}, false); }
    static CompanionRequest noActiveThreatQuestionWithStaleSnapshot() { return request(CompanionCommand.ANSWER_EXPOSURE, o -> o.snapshotFresh(false), false); }
    static CompanionRequest exposedZoneWithCopper(int copper) { return request(CompanionCommand.SUGGEST_BLUEPRINT, o -> o.defenseExposed(true).copper(copper), false); }
    static CompanionRequest freshPlacementPreflight() { return request(CompanionCommand.PREFLIGHT_BLUEPRINT, o -> {}, false); }
    static CompanionRequest stalePlacementPreflight() { return request(CompanionCommand.PREFLIGHT_BLUEPRINT, o -> o.snapshotFresh(false), false); }
    static CompanionRequest confirmedFreshPlacement() { return request(CompanionCommand.CONFIRM_BLUEPRINT, o -> {}, true); }
    static CompanionRequest unconfirmedFreshPlacement() { return request(CompanionCommand.CONFIRM_BLUEPRINT, o -> {}, false); }
    static CompanionRequest unbuildableRequestedZoneWithCoreFallback() { return request(CompanionCommand.PREFLIGHT_BLUEPRINT, o -> o.requestedZoneBuildable(false).coreFallbackBuildable(true), false); }
    static CompanionRequest noBuildableZoneOrCoreTiles() { return request(CompanionCommand.PREFLIGHT_BLUEPRINT, o -> o.requestedZoneBuildable(false), false); }

    static CompanionRequest assignEligiblePoly() { return request(CompanionCommand.ASSIGN_POLY, o -> {}, false); }
    static CompanionRequest assignWithoutEligiblePoly() { return request(CompanionCommand.ASSIGN_POLY, o -> o.eligiblePoly(false), false); }
    static CompanionRequest playerCancelsTravellingRepair() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.playerCancelled(true), false); }
    static CompanionRequest playerCancelsActiveRepair() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.playerCancelled(true), false); }
    static CompanionRequest playerRepurposesAssignedPoly() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.playerRepurposed(true), false); }
    static CompanionRequest hostAuthoritativeRepair() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.gatewayAssignmentConfirmed(true), true); }
    static CompanionRequest clientOnlySharedWorldRepair() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.hostAuthoritative(false), true); }

    static CompanionRequest repairTargetRestoredBeforeConfirmation() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.targetRestored(true), true); }
    static CompanionRequest repairTargetDestroyedBeforeConfirmation() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.targetDestroyed(true), true); }
    static CompanionRequest repairZoneBecomesDangerous() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.zoneDangerous(true), false); }
    static CompanionRequest polyRepairsTargetSuccessfully() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.targetRestored(true), false); }
    static CompanionRequest polyDiesDuringRepair() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.polyAlive(false), false); }
    static CompanionRequest pathBecomesBlockedDuringRepair() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.pathReachable(false), false); }
    static CompanionRequest polyAlreadyBuildingOrCarryingItems() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.polyBusy(true), true); }

    static CompanionRequest reachableDamagedFriendlyBuilding() { return request(CompanionCommand.PROPOSE_REPAIR, o -> {}, false); }
    static CompanionRequest unreachableOrInvalidRepairTarget() { return request(CompanionCommand.PROPOSE_REPAIR, o -> o.pathReachable(false), false); }
    static CompanionRequest confirmedFreshRepair() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.gatewayAssignmentConfirmed(true), true); }
    static CompanionRequest repairTargetRestored() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.targetRestored(true), false); }
    static CompanionRequest confirmedRepairForRestoredTarget() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.targetRestored(true), true); }
    static CompanionRequest multipleDamagedTargets() { return request(CompanionCommand.PROPOSE_REPAIR, o -> o.damagedTargetCount(3).selectedTargetId("core-adjacent-defense"), false); }

    static CompanionRequest repairRequiresUnavailableResources() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.resourcesAvailable(false), true); }
    static CompanionRequest repairInterruptedByCoreThreat() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.coreThreat(true), false); }
    static CompanionRequest repairInterruptedByPlayerCommand() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.playerCancelled(true), false); }
    static CompanionRequest newWaveBeginsDuringOptionalRepair() { return request(CompanionCommand.OBSERVE_REPAIR, o -> o.newWave(true), false); }
    static CompanionRequest repeatedExposureAlert() { return request(CompanionCommand.REPEAT_ALERT, o -> {}, false); }
    static CompanionRequest higherPriorityCoreThreat() { return request(CompanionCommand.REPEAT_ALERT, o -> o.coreThreat(true), false); }
    static CompanionRequest saveLoadWithValidRepairTask() { return request(CompanionCommand.RESUME_REPAIR, o -> {}, false); }
    static CompanionRequest mapRestartOrInvalidUnitReference() { return request(CompanionCommand.RESUME_REPAIR, o -> o.staleUnitReference(true), false); }
    static CompanionRequest gamePausedForThirtyWallClockSeconds() { return request(CompanionCommand.OBSERVE_REPAIR, o -> {}, false); }
    static CompanionRequest lowFpsButProgressingGameTicks() { return request(CompanionCommand.OBSERVE_REPAIR, o -> {}, false); }
    static CompanionRequest repairApprovedBeforeGatewayAssignment() { return request(CompanionCommand.CONFIRM_REPAIR, o -> {}, true); }
    static CompanionRequest gatewayConfirmsRepairAssignment() { return request(CompanionCommand.CONFIRM_REPAIR, o -> o.gatewayAssignmentConfirmed(true), true); }

    private static CompanionRequest request(CompanionCommand command, java.util.function.Consumer<CompanionObservation.Builder> configure, boolean confirmed) {
        final CompanionObservation.Builder builder = CompanionObservation.builder();
        configure.accept(builder);
        return new CompanionRequest(command, builder.build(), confirmed);
    }
}
