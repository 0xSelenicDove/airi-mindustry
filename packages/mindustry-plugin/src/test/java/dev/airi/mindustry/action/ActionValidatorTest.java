package dev.airi.mindustry.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ActionValidatorTest {
    @Test
    void acceptsAnActionAtTheFreshnessBoundary() {
        final ActionValidator.ValidationResult result = ActionValidator.validateSnapshotReference(1_000, "s-1-t820", 820);

        assertTrue(result.accepted());
        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void rejectsAnActionOlderThan180Ticks() {
        final ActionValidator.ValidationResult result = ActionValidator.validateSnapshotReference(1_000, "s-1-t819", 819);

        assertFalse(result.accepted());
        assertEquals("STALE_WORLD_STATE", result.status());
    }

    @Test
    void rejectsAMissingSnapshotReference() {
        final ActionValidator.ValidationResult result = ActionValidator.validateSnapshotReference(1_000, "", 1_000);

        assertFalse(result.accepted());
        assertEquals("INVALID_ACTION_REFERENCE", result.status());
    }
}
