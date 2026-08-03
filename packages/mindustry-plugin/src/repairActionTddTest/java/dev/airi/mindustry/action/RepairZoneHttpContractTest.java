package dev.airi.mindustry.action;

import dev.airi.mindustry.action.BlueprintExecutor.ActionResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED wire-format contract. The action endpoint must return a durable task ID
 * and current lifecycle state, rather than reporting placement coordinates.
 */
final class RepairZoneHttpContractTest {
    @Test
    void repairResponseContainsTaskIdAndLifecycleStatus() {
        assertAccessorExists("taskId");
        assertAccessorExists("taskStatus");
    }

    @Test
    void repairResponseDoesNotClaimRepairingBeforeGatewayAcceptance() {
        assertAccessorExists("acceptedAtTick");
    }

    private static void assertAccessorExists(final String name) {
        boolean found = false;
        for (Method method : ActionResult.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Action responses must expose '" + name + "' for REPAIR_ZONE clients.");
    }
}
