package dev.airi.mindustry.companion;

/** Player-facing lifecycle states for approved companion work. */
public enum CompanionTaskStatus {
    IDLE,
    CONFIRMATION_REQUIRED,
    DISPATCHING,
    TRAVELLING,
    REPAIRING,
    COMPLETE,
    NOT_NEEDED,
    INVALID_TARGET,
    INVALID_PLACEMENT,
    INSUFFICIENT_ITEMS,
    UNIT_UNAVAILABLE,
    UNIT_BUSY,
    UNREACHABLE,
    STALE_WORLD_STATE,
    UNSUPPORTED_SESSION,
    PAUSED_FOR_EMERGENCY,
    CANCELLED_BY_PLAYER,
    CLEARED_AFTER_REVALIDATION
}
