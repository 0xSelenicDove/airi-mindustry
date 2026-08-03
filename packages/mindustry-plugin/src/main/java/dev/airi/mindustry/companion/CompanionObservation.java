package dev.airi.mindustry.companion;

/**
 * Local, already-validated facts supplied by the Mindustry adapter. This core
 * deliberately does not inspect game objects or issue unit commands itself.
 */
public record CompanionObservation(
    boolean bridgeAvailable,
    boolean snapshotFresh,
    boolean defenseExposed,
    int copper,
    boolean requestedZoneBuildable,
    boolean coreFallbackBuildable,
    boolean eligiblePoly,
    boolean polyAlive,
    boolean polyBusy,
    boolean pathReachable,
    boolean targetValid,
    boolean targetRestored,
    boolean targetDestroyed,
    boolean zoneDangerous,
    boolean coreThreat,
    boolean newWave,
    boolean playerCancelled,
    boolean playerRepurposed,
    boolean hostAuthoritative,
    boolean resourcesAvailable,
    boolean gatewayAssignmentConfirmed,
    boolean persistedTaskValid,
    boolean staleUnitReference,
    int damagedTargetCount,
    String selectedTargetId
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean bridgeAvailable = true;
        private boolean snapshotFresh = true;
        private boolean defenseExposed;
        private int copper = 200;
        private boolean requestedZoneBuildable = true;
        private boolean coreFallbackBuildable;
        private boolean eligiblePoly = true;
        private boolean polyAlive = true;
        private boolean polyBusy;
        private boolean pathReachable = true;
        private boolean targetValid = true;
        private boolean targetRestored;
        private boolean targetDestroyed;
        private boolean zoneDangerous;
        private boolean coreThreat;
        private boolean newWave;
        private boolean playerCancelled;
        private boolean playerRepurposed;
        private boolean resourcesAvailable = true;
        private boolean hostAuthoritative = true;
        private boolean gatewayAssignmentConfirmed;
        private boolean persistedTaskValid = true;
        private boolean staleUnitReference;
        private int damagedTargetCount = 1;
        private String selectedTargetId = "repair-target";

        public Builder bridgeAvailable(boolean value) { bridgeAvailable = value; return this; }
        public Builder snapshotFresh(boolean value) { snapshotFresh = value; return this; }
        public Builder defenseExposed(boolean value) { defenseExposed = value; return this; }
        public Builder copper(int value) { copper = value; return this; }
        public Builder requestedZoneBuildable(boolean value) { requestedZoneBuildable = value; return this; }
        public Builder coreFallbackBuildable(boolean value) { coreFallbackBuildable = value; return this; }
        public Builder eligiblePoly(boolean value) { eligiblePoly = value; return this; }
        public Builder polyAlive(boolean value) { polyAlive = value; return this; }
        public Builder polyBusy(boolean value) { polyBusy = value; return this; }
        public Builder pathReachable(boolean value) { pathReachable = value; return this; }
        public Builder targetValid(boolean value) { targetValid = value; return this; }
        public Builder targetRestored(boolean value) { targetRestored = value; return this; }
        public Builder targetDestroyed(boolean value) { targetDestroyed = value; return this; }
        public Builder zoneDangerous(boolean value) { zoneDangerous = value; return this; }
        public Builder coreThreat(boolean value) { coreThreat = value; return this; }
        public Builder newWave(boolean value) { newWave = value; return this; }
        public Builder playerCancelled(boolean value) { playerCancelled = value; return this; }
        public Builder playerRepurposed(boolean value) { playerRepurposed = value; return this; }
        public Builder hostAuthoritative(boolean value) { hostAuthoritative = value; return this; }
        public Builder resourcesAvailable(boolean value) { resourcesAvailable = value; return this; }
        public Builder gatewayAssignmentConfirmed(boolean value) { gatewayAssignmentConfirmed = value; return this; }
        public Builder persistedTaskValid(boolean value) { persistedTaskValid = value; return this; }
        public Builder staleUnitReference(boolean value) { staleUnitReference = value; return this; }
        public Builder damagedTargetCount(int value) { damagedTargetCount = value; return this; }
        public Builder selectedTargetId(String value) { selectedTargetId = value; return this; }

        public CompanionObservation build() {
            return new CompanionObservation(bridgeAvailable, snapshotFresh, defenseExposed, copper, requestedZoneBuildable,
                coreFallbackBuildable, eligiblePoly, polyAlive, polyBusy, pathReachable, targetValid, targetRestored,
                targetDestroyed, zoneDangerous, coreThreat, newWave, playerCancelled, playerRepurposed, hostAuthoritative,
                resourcesAvailable, gatewayAssignmentConfirmed, persistedTaskValid, staleUnitReference, damagedTargetCount,
                selectedTargetId);
        }
    }
}
