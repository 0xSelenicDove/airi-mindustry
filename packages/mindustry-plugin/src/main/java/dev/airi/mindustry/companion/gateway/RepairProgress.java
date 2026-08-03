package dev.airi.mindustry.companion.gateway;

/** Current physical progress reported by the game-thread repair adapter. */
public enum RepairProgress {
    DISPATCHING,
    TRAVELLING,
    REPAIRING,
    COMPLETE,
    INVALID_TARGET,
    UNIT_UNAVAILABLE,
    UNREACHABLE
}
