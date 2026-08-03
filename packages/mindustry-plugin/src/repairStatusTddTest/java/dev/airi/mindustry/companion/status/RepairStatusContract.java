package dev.airi.mindustry.companion.status;

import dev.airi.mindustry.companion.CompanionTaskStatus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only facade for the production status API to be introduced by this
 * milestone. Reflection keeps the RED suite compilable before that API exists.
 */
final class RepairStatusContract {
    private static final String ENDPOINT = "dev.airi.mindustry.companion.status.RepairTaskStatusEndpoint";
    private static final String BRIDGE = "dev.airi.mindustry.companion.status.RepairStatusBridge";

    private RepairStatusContract() { }

    static void beginSession() {
        invoke("dev.airi.mindustry.companion.status.RepairStatusBridge", "resetSession", new Class<?>[]{}, new Object[]{}, Void.class);
    }

    static StatusReply get(final String taskId) {
        return toReply(invoke(ENDPOINT, "get", new Class<?>[]{String.class}, taskId, Object.class));
    }

    static StatusReply getWithMethod(final String method, final String taskId) {
        return toReply(invoke(ENDPOINT, "handle", new Class<?>[]{String.class, String.class}, method, taskId, Object.class));
    }

    static List<StatusEvent> pollBridge(final long afterSequence) {
        final List<?> rawEvents = invoke(BRIDGE, "eventsAfter", new Class<?>[]{long.class}, afterSequence, List.class);
        final List<StatusEvent> events = new ArrayList<>();
        for (Object event : rawEvents) {
            events.add(new StatusEvent((long) accessor(event, "sequence"), (String) accessor(event, "taskId"),
                (CompanionTaskStatus) accessor(event, "taskStatus"), (long) accessor(event, "gameTick"), (String) accessor(event, "reason")));
        }
        return events;
    }

    static void observe(final String taskId, final CompanionTaskStatus status, final long tick, final String reason) {
        invoke(BRIDGE, "observe", new Class<?>[]{String.class, CompanionTaskStatus.class, long.class, String.class},
            taskId, status, tick, reason, Void.class);
    }

    static StatusReply resumeAfterBridgeRestart(final String taskId) {
        return toReply(invoke(BRIDGE, "restoreCurrentTask", new Class<?>[]{String.class}, taskId, Object.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(final String className, final String methodName, final Class<?>[] parameterTypes,
        final Object first, final Class<T> returnType) {
        return invoke(className, methodName, parameterTypes, new Object[]{first}, returnType);
    }

    private static StatusReply toReply(final Object reply) {
        return new StatusReply((int) accessor(reply, "httpStatus"), (String) accessor(reply, "reasonCode"),
            (String) accessor(reply, "taskId"), (CompanionTaskStatus) accessor(reply, "taskStatus"),
            (String) accessor(reply, "message"), (long) accessor(reply, "gameTick"), (boolean) accessor(reply, "airiControlReleased"));
    }

    private static Object accessor(final Object value, final String name) {
        try {
            return value.getClass().getMethod(name).invoke(value);
        }
        catch (ReflectiveOperationException error) {
            throw new AssertionError("Status response is missing '" + name + "'.", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(final String className, final String methodName, final Class<?>[] parameterTypes,
        final Object first, final Object second, final Class<T> returnType) {
        return invoke(className, methodName, parameterTypes, new Object[]{first, second}, returnType);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(final String className, final String methodName, final Class<?>[] parameterTypes,
        final Object first, final Object second, final Object third, final Object fourth, final Class<T> returnType) {
        return invoke(className, methodName, parameterTypes, new Object[]{first, second, third, fourth}, returnType);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(final String className, final String methodName, final Class<?>[] parameterTypes,
        final Object[] arguments, final Class<T> returnType) {
        try {
            final Class<?> type = Class.forName(className);
            final Method method = type.getMethod(methodName, parameterTypes);
            final Object result = method.invoke(null, arguments);
            return result == null && returnType == Void.class ? null : (T) result;
        }
        catch (ClassNotFoundException error) {
            throw new AssertionError("Missing production status API: " + className, error);
        }
        catch (NoSuchMethodException error) {
            throw new AssertionError("Missing status API method: " + className + "." + methodName, error);
        }
        catch (IllegalAccessException | InvocationTargetException error) {
            throw new AssertionError("Status API invocation failed: " + className + "." + methodName, error);
        }
    }

    record StatusReply(int httpStatus, String reasonCode, String taskId, CompanionTaskStatus taskStatus,
                       String message, long gameTick, boolean airiControlReleased) { }
    record StatusEvent(long sequence, String taskId, CompanionTaskStatus taskStatus, long gameTick, String reason) { }
}
