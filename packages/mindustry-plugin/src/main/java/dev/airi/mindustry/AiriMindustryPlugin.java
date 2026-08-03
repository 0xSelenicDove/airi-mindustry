package dev.airi.mindustry;

import arc.util.Log;
import arc.Core;
import mindustry.game.EventType.Trigger;
import arc.Events;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mindustry.mod.Mod;
import dev.airi.mindustry.state.StateCollector;
import dev.airi.mindustry.action.ActionRouter;
import dev.airi.mindustry.action.BlueprintExecutor.ActionResult;
import dev.airi.mindustry.companion.status.RepairTaskStatusEndpoint;
import dev.airi.mindustry.action.safety.ActionSafetyGateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exposes a minimal, read-only client-game snapshot to a local AIRI bridge.
 *
 * The listener is loopback-only because a Mindustry JVM mod has unrestricted JVM
 * access. Mindustry state is captured on its update thread; HTTP requests only
 * read the immutable cached JSON snapshot and never access game state directly.
 * It exposes no game-control operations in this milestone.
 */
public final class AiriMindustryPlugin extends Mod {
    private static final int PORT = 18231;
    private static final int MAX_ACTION_BODY_BYTES = 16 * 1024;
    private final StateCollector stateCollector = new StateCollector();
    private final ActionRouter actionRouter = new ActionRouter();
    private volatile String latestStateJson = StateCollector.inactiveStateJson();

    @Override
    public void init() {
        Events.run(Trigger.update, () -> {
            latestStateJson = stateCollector.collect();
            actionRouter.refreshRepairStatuses((long) mindustry.Vars.state.tick);
        });

        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/v1/state", this::handleState);
            server.createContext("/v1/action/submit", this::handleActionSubmit);
            server.createContext("/v1/repair/tasks", this::handleRepairTaskStatus);
            server.createContext("/v1/action/stop", this::handleActionStop);
            server.createContext("/v1/audit", this::handleAudit);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            Log.info("AIRI Mindustry client bridge listening at http://127.0.0.1:@", PORT);
        }
        catch (IOException error) {
            Log.err("Unable to start AIRI Mindustry state bridge.", error);
        }
    }

    private void handleState(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        final byte[] body = latestStateJson.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Handles the one explicit, confirmed action supported by this milestone.
     * Parsing is done on the HTTP thread; all game-state access and execution
     * are posted to the Mindustry update thread.
     */
    private void handleActionSubmit(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        final byte[] body = exchange.getRequestBody().readNBytes(MAX_ACTION_BODY_BYTES + 1);
        if (body.length > MAX_ACTION_BODY_BYTES) {
            sendActionResponse(exchange, new ActionResult("rejected", "REQUEST_TOO_LARGE", "Action request body exceeds 16 KiB.", null, null));
            return;
        }

        final AtomicReference<ActionResult> result = new AtomicReference<>();
        final CountDownLatch complete = new CountDownLatch(1);
        Core.app.post(() -> {
            try {
                result.set(actionRouter.submit(new String(body, StandardCharsets.UTF_8)));
            }
            finally {
                complete.countDown();
            }
        });
        try {
            if (!complete.await(2, TimeUnit.SECONDS)) {
                sendActionResponse(exchange, new ActionResult("rejected", "GAME_THREAD_TIMEOUT", "The game did not process the action in time.", null, null));
                return;
            }
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            sendActionResponse(exchange, new ActionResult("rejected", "REQUEST_INTERRUPTED", "Action request was interrupted.", null, null));
            return;
        }
        sendActionResponse(exchange, result.get());
    }

    private void handleRepairTaskStatus(HttpExchange exchange) throws IOException {
        final String prefix = "/v1/repair/tasks/";
        final String path = exchange.getRequestURI().getPath();
        final String taskId = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
        final RepairTaskStatusEndpoint.StatusReply result = RepairTaskStatusEndpoint.handle(exchange.getRequestMethod(), taskId);
        if (!"GET".equals(exchange.getRequestMethod())) exchange.getResponseHeaders().set("Allow", "GET");
        final String json = String.format(
            "{\"status\":\"%s\",\"reasonCode\":\"%s\",\"taskId\":%s,\"taskStatus\":%s,\"message\":\"%s\",\"gameTick\":%d,\"airiControlReleased\":%s}",
            escapeJson(result.httpStatus() == 200 ? "accepted" : "rejected"), escapeJson(result.reasonCode()),
            nullableJson(result.taskId()), nullableJson(result.taskStatus() == null ? null : result.taskStatus().name()),
            escapeJson(result.message()), result.gameTick(), result.airiControlReleased());
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(result.httpStatus(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleActionStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        final AtomicReference<ActionSafetyGateway.StopReply> result = new AtomicReference<>();
        final CountDownLatch complete = new CountDownLatch(1);
        Core.app.post(() -> {
            try { result.set(ActionSafetyGateway.stop((long) mindustry.Vars.state.tick)); }
            finally { complete.countDown(); }
        });
        try {
            if (!complete.await(2, TimeUnit.SECONDS)) {
                sendSafetyJson(exchange, 503, "GAME_THREAD_TIMEOUT", java.util.List.of(), 0);
                return;
            }
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            sendSafetyJson(exchange, 503, "REQUEST_INTERRUPTED", java.util.List.of(), 0);
            return;
        }
        final ActionSafetyGateway.StopReply stopped = result.get();
        sendSafetyJson(exchange, "accepted".equals(stopped.status()) ? 200 : 409,
            "accepted".equals(stopped.status()) ? "ACCEPTED" : "UNSUPPORTED_SESSION", stopped.stoppedTaskIds(), 0);
    }

    private void handleAudit(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        final ActionSafetyGateway.HttpReply reply = ActionSafetyGateway.handleHttp("GET", exchange.getRequestURI().toString());
        sendAuditJson(exchange, reply.httpStatus(), reply.reasonCode(),
            ActionSafetyGateway.audit(queryLong(exchange.getRequestURI().getQuery(), "before", Long.MAX_VALUE), reply.pageSize()), reply.pageSize());
    }

    private void sendSafetyJson(HttpExchange exchange, int status, String reasonCode, java.util.List<String> taskIds, int pageSize) throws IOException {
        final String ids = taskIds.stream().map(id -> "\"" + escapeJson(id) + "\"").collect(java.util.stream.Collectors.joining(","));
        final String json = String.format("{\"status\":\"%s\",\"reasonCode\":\"%s\",\"stoppedTaskIds\":[%s],\"count\":%d,\"pageSize\":%d}",
            status == 200 ? "accepted" : "rejected", escapeJson(reasonCode), ids, taskIds.size(), pageSize);
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendAuditJson(HttpExchange exchange, int status, String reasonCode, java.util.List<ActionSafetyGateway.AuditEntry> entries,
        int pageSize) throws IOException {
        final String events = entries.stream().map(entry -> String.format(
            "{\"sequence\":%d,\"actionId\":\"%s\",\"actionType\":\"%s\",\"gameTick\":%d,\"status\":\"%s\",\"reasonCode\":\"%s\",\"taskId\":%s,\"mutationPerformed\":%s,\"sessionId\":\"%s\"}",
            entry.sequence(), escapeJson(entry.actionId()), escapeJson(entry.actionType()), entry.gameTick(), escapeJson(entry.status()),
            escapeJson(entry.reasonCode()), nullableJson(entry.taskId()), entry.mutationPerformed(), escapeJson(entry.sessionId())))
            .collect(java.util.stream.Collectors.joining(","));
        final String json = String.format("{\"status\":\"%s\",\"reasonCode\":\"%s\",\"entries\":[%s],\"pageSize\":%d}",
            status == 200 ? "accepted" : "rejected", escapeJson(reasonCode), events, pageSize);
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendActionResponse(HttpExchange exchange, ActionResult result) throws IOException {
        final String target = result.targetX() == null
            ? ""
            : String.format(",\"resolvedTarget\":{\"x\":%d,\"y\":%d}", result.targetX(), result.targetY());
        final String repairTask = result.taskId() == null ? "" : String.format(
            ",\"taskId\":\"%s\",\"taskStatus\":\"%s\",\"acceptedAtTick\":%d",
            escapeJson(result.taskId()), escapeJson(result.taskStatus()), result.acceptedAtTick());
        final String json = String.format(
            "{\"status\":\"%s\",\"reasonCode\":\"%s\",\"message\":\"%s\"%s%s}",
            escapeJson(result.status()), escapeJson(result.reasonCode()), escapeJson(result.message()), target, repairTask
        );
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String nullableJson(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private static long queryLong(String query, String name, long fallback) {
        if (query == null) return fallback;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                try { return Long.parseLong(parts[1]); }
                catch (NumberFormatException ignored) { return fallback; }
            }
        }
        return fallback;
    }

}
