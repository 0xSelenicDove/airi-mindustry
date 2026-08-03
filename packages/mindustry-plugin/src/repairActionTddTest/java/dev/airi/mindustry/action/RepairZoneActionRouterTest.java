package dev.airi.mindustry.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RED contract for adding REPAIR_ZONE to the narrow action allowlist. Each
 * method mirrors one player-visible routing/preflight outcome.
 */
final class RepairZoneActionRouterTest {
    @Test
    void unconfirmedRepairReturnsConfirmationRequired() {
        assertReason("CONFIRMATION_REQUIRED", routerFor(100, "confirmation-required", "CONFIRMATION_REQUIRED"), request(100, false, 7, 11));
    }

    @Test
    void confirmedRepairReturnsDispatchingTask() {
        assertStatus("dispatching", routerFor(100, "dispatching", "DISPATCHING"), request(100, true, 7, 11));
    }

    @Test
    void staleRepairActionIsRejectedBeforeDispatch() {
        assertReason("STALE_WORLD_STATE", routerFor(281, "dispatching", "DISPATCHING"), request(100, true, 7, 11));
    }

    @Test
    void missingTargetIsRejected() {
        assertReason("INVALID_TARGET", routerFor(100, "dispatching", "DISPATCHING"), request(100, true, 7, -1));
    }

    @Test
    void nonPolyOrBusyPolyIsRejected() {
        assertReason("UNIT_UNAVAILABLE", routerFor(100, "dispatching", "DISPATCHING"), request(100, true, -1, 11));
    }

    @Test
    void clientOnlyMultiplayerIsRejectedBeforeAnyUnitCommand() {
        assertReason("UNSUPPORTED_SESSION", routerFor(100, "rejected", "UNSUPPORTED_SESSION"), request(100, true, 7, 11));
    }

    @Test
    void duplicateSubmitIsIdempotent() {
        final ActionRouter router = routerFor(100, "dispatching", "DISPATCHING");
        final var firstResponse = router.submit(request(100, true, 7, 11));
        final var retryResponse = router.submit(request(100, true, 7, 11));
        assertEquals("dispatching", firstResponse.status());
        assertEquals("dispatching", retryResponse.status());
        assertEquals(firstResponse.message(), retryResponse.message(),
            "A retry must return the original task, not create another repair task.");
    }

    private void assertReason(final String expected, final ActionRouter router, final String body) {
        assertEquals(expected, router.submit(body).reasonCode());
    }

    private void assertStatus(final String expected, final ActionRouter router, final String body) {
        assertEquals(expected, router.submit(body).status());
    }

    private static String request(final long refTick, final boolean confirmed, final int polyId, final int targetId) {
        return """
            {"version":1,"type":"REPAIR_ZONE","idempotencyKey":"repair-1",
             "refSnapshotId":"s-1-t100","refTick":%d,"confirmed":%s,
             "target":{"buildingId":%d},"payload":{"polyId":%d}}
            """.formatted(refTick, confirmed, targetId, polyId);
    }

    private static ActionRouter routerFor(final long tick, final String status, final String reasonCode) {
        return new ActionRouter(() -> tick, (polyId, buildingId, refTick, confirmed, key) ->
            new BlueprintExecutor.ActionResult(status, reasonCode, "Repair approved; dispatching Poly.", null, null,
                "repair-1", "DISPATCHING", tick));
    }
}
