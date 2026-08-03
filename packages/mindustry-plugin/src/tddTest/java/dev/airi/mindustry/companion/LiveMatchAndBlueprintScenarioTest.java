package dev.airi.mindustry.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Current-match reads and confirmed blueprint UX. */
final class LiveMatchAndBlueprintScenarioTest extends CompanionScenarioTestSupport {
    @Test void liveExposureQuestion() {
        final CompanionOutcome happy = controller().handle(CompanionScenario.exposedBaseQuestion());
        assertTrue(happy.toolCalls().contains("get_mindustry_context"));
        assertTrue(happy.toolCalls().contains("get_mindustry_events"));
        assertTrue(happy.message().contains("exposed"));
        final CompanionOutcome down = controller().handle(CompanionScenario.exposedBaseQuestionWithBridgeOffline());
        assertFalse(down.message().contains("no enemies"));
        assertTrue(down.message().contains("local match data is unavailable"));
    }

    @Test void noActiveThreat() {
        final CompanionOutcome happy = controller().handle(CompanionScenario.noActiveThreatQuestion());
        assertTrue(happy.toolCalls().contains("get_mindustry_events"));
        assertTrue(happy.message().contains("No active exposure"));
        final CompanionOutcome stale = controller().handle(CompanionScenario.noActiveThreatQuestionWithStaleSnapshot());
        assertTrue(stale.message().contains("fresh local state"));
        assertFalse(stale.message().contains("No active exposure"));
    }

    @Test void defenseSuggestion() {
        final CompanionOutcome happy = controller().handle(CompanionScenario.exposedZoneWithCopper(70));
        assertEquals("starter-duo-defense", happy.proposal().schematicId());
        assertTrue(happy.proposal().requiresConfirmation());
        final CompanionOutcome insufficient = controller().handle(CompanionScenario.exposedZoneWithCopper(69));
        assertFalse(insufficient.hasProposal());
        assertTrue(insufficient.message().contains("need 1 more copper"));
    }

    @Test void placementPreflight() {
        final CompanionOutcome happy = controller().handle(CompanionScenario.freshPlacementPreflight());
        assertEquals(CompanionTaskStatus.CONFIRMATION_REQUIRED, happy.taskStatus());
        assertTrue(happy.resolvedTarget().isPresent());
        final CompanionOutcome stale = controller().handle(CompanionScenario.stalePlacementPreflight());
        assertEquals(CompanionTaskStatus.STALE_WORLD_STATE, stale.taskStatus());
        assertFalse(stale.worldMutationAttempted());
    }

    @Test void placementConfirmation() {
        final CompanionOutcome happy = controller().handle(CompanionScenario.confirmedFreshPlacement());
        assertEquals(CompanionTaskStatus.COMPLETE, happy.taskStatus());
        assertTrue(happy.worldMutationAttempted());
        assertEquals(64, happy.itemsSpent("copper"));
        final CompanionOutcome notConfirmed = controller().handle(CompanionScenario.unconfirmedFreshPlacement());
        assertEquals(CompanionTaskStatus.CONFIRMATION_REQUIRED, notConfirmed.taskStatus());
        assertFalse(notConfirmed.worldMutationAttempted());
        assertEquals(0, notConfirmed.itemsSpent("copper"));
    }

    @Test void terrainFallback() {
        final CompanionOutcome fallback = controller().handle(CompanionScenario.unbuildableRequestedZoneWithCoreFallback());
        assertEquals(CompanionTaskStatus.CONFIRMATION_REQUIRED, fallback.taskStatus());
        assertTrue(fallback.message().contains("near the player core"));
        assertTrue(fallback.resolvedTarget().isPresent());
        final CompanionOutcome blocked = controller().handle(CompanionScenario.noBuildableZoneOrCoreTiles());
        assertEquals(CompanionTaskStatus.INVALID_PLACEMENT, blocked.taskStatus());
        assertFalse(blocked.resolvedTarget().isPresent());
    }
}
