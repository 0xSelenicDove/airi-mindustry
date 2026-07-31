package dev.airi.mindustry.action;

/**
 * Deterministic action preflight rules. Future action handlers must invoke this
 * before checking resources, placement, ownership, or execution.
 */
public final class ActionValidator {
    /** At 60 simulation updates per second, this is approximately three seconds. */
    public static final long MAX_ACTION_AGE_TICKS = 180;

    private ActionValidator() {}

    public static ValidationResult validateSnapshotReference(long currentTick, String referenceSnapshotId, long referenceTick) {
        if (referenceSnapshotId == null || referenceSnapshotId.isBlank() || referenceTick < 0)
            return ValidationResult.rejected("INVALID_ACTION_REFERENCE", "Actions must include refSnapshotId and refTick.");
        if (currentTick < referenceTick)
            return ValidationResult.rejected("INVALID_ACTION_REFERENCE", "The action references a future game tick.");
        if (currentTick - referenceTick > MAX_ACTION_AGE_TICKS)
            return ValidationResult.rejected("STALE_WORLD_STATE", "The referenced game state is more than 180 ticks old.");
        return ValidationResult.allow();
    }

    public record ValidationResult(boolean accepted, String status, String message) {
        private static ValidationResult allow() {
            return new ValidationResult(true, "ACCEPTED", null);
        }

        private static ValidationResult rejected(String status, String message) {
            return new ValidationResult(false, status, message);
        }
    }
}
