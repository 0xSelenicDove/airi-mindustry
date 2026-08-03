package dev.airi.mindustry.action.safety;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Test-only facade for the safety API introduced by the next implementation slice. */
final class ActionSafetyContract {
    private static final String GATEWAY = "dev.airi.mindustry.action.safety.ActionSafetyGateway";

    private ActionSafetyContract() { }

    static void beginSession(final boolean hostAuthoritative, final int perTickLimit) {
        invoke("beginSession", new Class<?>[]{boolean.class, int.class}, hostAuthoritative, perTickLimit);
    }

    static void startTask(final String taskId, final String type, final long tick, final boolean airiControlled) {
        invoke("startTask", new Class<?>[]{String.class, String.class, long.class, boolean.class}, taskId, type, tick, airiControlled);
    }

    static Reply submit(final String actionId, final String type, final long tick, final String idempotencyKey) {
        return toReply(invoke("submit", new Class<?>[]{String.class, String.class, long.class, String.class}, actionId, type, tick, idempotencyKey));
    }

    static StopReply stop(final long tick) {
        return toStopReply(invoke("stop", new Class<?>[]{long.class}, tick));
    }

    static List<AuditEntry> audit(final long beforeSequence, final int limit) {
        final List<?> raw = castList(invoke("audit", new Class<?>[]{long.class, int.class}, beforeSequence, limit));
        final List<AuditEntry> entries = new ArrayList<>();
        for (Object value : raw) entries.add(toAuditEntry(value));
        return entries;
    }

    static HttpReply http(final String method, final String path) {
        return toHttpReply(invoke("handleHttp", new Class<?>[]{String.class, String.class}, method, path));
    }

    private static Object invoke(final String methodName, final Class<?>[] parameterTypes, final Object... arguments) {
        try {
            final Method method = Class.forName(GATEWAY).getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        }
        catch (ClassNotFoundException error) {
            throw new AssertionError("Missing production safety API: " + GATEWAY, error);
        }
        catch (NoSuchMethodException error) {
            throw new AssertionError("Missing safety API method: " + methodName, error);
        }
        catch (IllegalAccessException | InvocationTargetException error) {
            throw new AssertionError("Safety API invocation failed: " + methodName, error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> castList(final Object value) { return (List<?>) value; }

    private static Reply toReply(final Object value) {
        return new Reply((String) accessor(value, "status"), (String) accessor(value, "reasonCode"),
            (String) accessor(value, "taskId"), (boolean) accessor(value, "mutationPerformed"));
    }

    private static StopReply toStopReply(final Object value) {
        return new StopReply((String) accessor(value, "status"), (List<String>) accessor(value, "stoppedTaskIds"),
            (int) accessor(value, "stoppedCount"), (boolean) accessor(value, "playerControlReleased"));
    }

    private static AuditEntry toAuditEntry(final Object value) {
        return new AuditEntry((long) accessor(value, "sequence"), (String) accessor(value, "actionId"),
            (String) accessor(value, "actionType"), (long) accessor(value, "gameTick"),
            (String) accessor(value, "status"), (String) accessor(value, "reasonCode"),
            (String) accessor(value, "taskId"), (boolean) accessor(value, "mutationPerformed"),
            (String) accessor(value, "sessionId"));
    }

    private static HttpReply toHttpReply(final Object value) {
        return new HttpReply((int) accessor(value, "httpStatus"), (String) accessor(value, "reasonCode"),
            (List<String>) accessor(value, "stoppedTaskIds"), (int) accessor(value, "pageSize"));
    }

    private static Object accessor(final Object value, final String name) {
        try { return value.getClass().getMethod(name).invoke(value); }
        catch (ReflectiveOperationException error) { throw new AssertionError("Safety response is missing '" + name + "'.", error); }
    }

    record Reply(String status, String reasonCode, String taskId, boolean mutationPerformed) { }
    record StopReply(String status, List<String> stoppedTaskIds, int stoppedCount, boolean playerControlReleased) { }
    record AuditEntry(long sequence, String actionId, String actionType, long gameTick, String status, String reasonCode,
                      String taskId, boolean mutationPerformed, String sessionId) { }
    record HttpReply(int httpStatus, String reasonCode, List<String> stoppedTaskIds, int pageSize) { }
}
