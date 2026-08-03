package dev.airi.mindustry.companion.gateway;

import dev.airi.mindustry.companion.CompanionTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Red integration contract for the first physical AIRI-companion feature.
 *
 * <p>The future {@code HostRepairGateway} must run on the authoritative game
 * host. {@code MindustryRepairFixture} creates a real deterministic Mindustry
 * world with a friendly Poly and a damageable friendly building; it must not
 * mock pathing, unit ownership, health, or task acceptance.</p>
 */
final class RepairZoneGatewayIntegrationTest {

    @Test
    void confirmedRepairDispatchesPolyAndRestoresBuildingHealth() {
        try (MindustryRepairFixture fixture = MindustryRepairFixture.singlePlayerHost()) {
            final RepairTarget target = fixture.placeDamagedFriendlyWall(60, 60, 40f);
            final CompanionUnit poly = fixture.spawnEligiblePoly(40, 40);
            final HostRepairGateway gateway = fixture.gateway();

            final RepairGatewayResult dispatch = gateway.submit(RepairZoneCommand.confirmed(poly.id(), target.id(), fixture.currentTick()));
            assertEquals(CompanionTaskStatus.DISPATCHING, dispatch.status());
            assertTrue(dispatch.message().contains("dispatching"));
            assertFalse(dispatch.message().contains("repairing"));
            assertTrue(fixture.isGatewayControlling(poly));

            fixture.tickUntil(() -> gateway.status(dispatch.taskId()).status() == CompanionTaskStatus.TRAVELLING);
            fixture.tickUntil(() -> gateway.status(dispatch.taskId()).status() == CompanionTaskStatus.REPAIRING);
            fixture.tickUntil(() -> gateway.status(dispatch.taskId()).status() == CompanionTaskStatus.COMPLETE);

            assertEquals(target.maxHealth(), fixture.health(target));
            assertFalse(fixture.isGatewayControlling(poly));
        }
    }

    @Test
    void playerCancellationReleasesPolyControlImmediately() {
        try (MindustryRepairFixture fixture = MindustryRepairFixture.singlePlayerHost()) {
            final RepairTarget target = fixture.placeDamagedFriendlyWall(60, 60, 40f);
            final CompanionUnit poly = fixture.spawnEligiblePoly(40, 40);
            final HostRepairGateway gateway = fixture.gateway();
            final RepairGatewayResult dispatch = gateway.submit(RepairZoneCommand.confirmed(poly.id(), target.id(), fixture.currentTick()));

            fixture.tickUntil(() -> gateway.status(dispatch.taskId()).status() == CompanionTaskStatus.TRAVELLING);
            final RepairGatewayResult cancelled = gateway.cancel(dispatch.taskId(), fixture.currentTick());

            assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, cancelled.status());
            assertFalse(fixture.isGatewayControlling(poly));
            assertTrue(fixture.canPlayerCommand(poly));
            assertTrue(fixture.health(target) < target.maxHealth());
        }
    }

    @Test
    void manualPlayerRepurposeReleasesPolyControlImmediately() {
        try (MindustryRepairFixture fixture = MindustryRepairFixture.singlePlayerHost()) {
            final RepairTarget target = fixture.placeDamagedFriendlyWall(60, 60, 40f);
            final CompanionUnit poly = fixture.spawnEligiblePoly(40, 40);
            final HostRepairGateway gateway = fixture.gateway();
            final RepairGatewayResult dispatch = gateway.submit(RepairZoneCommand.confirmed(poly.id(), target.id(), fixture.currentTick()));

            fixture.tickUntil(() -> gateway.status(dispatch.taskId()).status() == CompanionTaskStatus.TRAVELLING);
            fixture.issuePlayerMoveOrder(poly, 90, 90);

            assertEquals(CompanionTaskStatus.CANCELLED_BY_PLAYER, gateway.status(dispatch.taskId()).status());
            assertFalse(fixture.isGatewayControlling(poly));
            assertTrue(fixture.canPlayerCommand(poly));
        }
    }

    @Test
    void clientOnlySharedWorldRepairIsRejectedBeforeAnyUnitCommand() {
        try (MindustryRepairFixture fixture = MindustryRepairFixture.multiplayerClient()) {
            final RepairTarget target = fixture.placeDamagedFriendlyWall(60, 60, 40f);
            final CompanionUnit poly = fixture.spawnEligiblePoly(40, 40);

            final RepairGatewayResult rejected = fixture.gateway().submit(RepairZoneCommand.confirmed(poly.id(), target.id(), fixture.currentTick()));

            assertEquals(CompanionTaskStatus.UNSUPPORTED_SESSION, rejected.status());
            assertFalse(fixture.isGatewayControlling(poly));
            assertFalse(fixture.unitReceivedCommand(poly));
            assertEquals(40f, fixture.health(target));
        }
    }
}
