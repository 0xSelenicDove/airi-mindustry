package dev.airi.mindustry.companion;

/** Stable local priority order. Higher entries may preempt lower companion work. */
public enum CompanionPriority {
    OPTIONAL_WORK,
    DEFENSE_READINESS,
    ACTIVE_DEFENSE_REPAIR,
    CORE_SURVIVAL
}
