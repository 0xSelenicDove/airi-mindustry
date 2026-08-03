package dev.airi.mindustry.action.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActionAuditHttpContractTest {
    @BeforeEach void beginSession() { ActionSafetyContract.beginSession(true, 2); }

    @Test
    void auditEndpointIsReadOnlyAndBoundsItsPageSize() {
        final var response = ActionSafetyContract.http("GET", "/v1/audit?limit=1000");
        assertEquals(200, response.httpStatus());
        assertTrue(response.pageSize() <= 100);
    }

    @Test
    void emergencyStopEndpointReturnsStoppedTaskIdsAndCount() {
        ActionSafetyContract.startTask("repair-1", "REPAIR_ZONE", 100, true);
        final var response = ActionSafetyContract.http("POST", "/v1/action/stop");
        assertEquals(200, response.httpStatus());
        assertEquals(1, response.stoppedTaskIds().size());
    }
}
