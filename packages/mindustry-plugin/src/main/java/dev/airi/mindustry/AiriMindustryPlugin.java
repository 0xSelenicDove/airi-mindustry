package dev.airi.mindustry;

import arc.util.Log;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.mod.Plugin;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.concurrent.Executors;

/**
 * Exposes a minimal, read-only game snapshot to a local AIRI bridge.
 *
 * The listener is loopback-only because a Mindustry plugin has unrestricted JVM access.
 * It exposes no game-control operations in this milestone.
 */
public final class AiriMindustryPlugin extends Plugin {
    private static final int PORT = 18231;

    @Override
    public void init() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/v1/state", this::handleState);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            Log.info("AIRI Mindustry state bridge listening at http://127.0.0.1:@", PORT);
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

        final String response = String.format(
            "{\"gameRunning\":%s,\"mapName\":%s,\"wave\":%d,\"waveTime\":%.2f,\"playerCount\":%d,\"players\":%s,\"unitCount\":%d,\"enemyUnitCount\":%d,\"core\":%s}",
            Vars.state.isGame(),
            mapNameJson(),
            Vars.state.wave,
            Vars.state.wavetime,
            Groups.player.size(),
            playerNamesJson(),
            Groups.unit.size(),
            countEnemyUnits(Vars.state.rules.defaultTeam),
            coreJson(Vars.state.rules.defaultTeam)
        );
        final byte[] body = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String playerNamesJson() {
        final StringJoiner names = new StringJoiner(",", "[", "]");
        Groups.player.each(player -> names.add("\"" + escapeJson(player.name) + "\""));
        return names.toString();
    }

    private String mapNameJson() {
        if (Vars.state.map == null)
            return "null";

        return "\"" + escapeJson(Vars.state.map.plainName()) + "\"";
    }

    private int countEnemyUnits(Team playerTeam) {
        final int[] enemyCount = { 0 };
        Groups.unit.each(unit -> {
            if (unit.team != playerTeam)
                enemyCount[0]++;
        });
        return enemyCount[0];
    }

    private String coreJson(Team playerTeam) {
        final CoreBuild core = playerTeam.core();
        if (core == null)
            return "null";

        final StringJoiner inventory = new StringJoiner(",", "[", "]");
        for (Item item : Vars.content.items()) {
            final int amount = core.items.get(item);
            if (amount > 0) {
                inventory.add(String.format(
                    "{\"name\":\"%s\",\"amount\":%d}",
                    escapeJson(item.name),
                    amount
                ));
            }
        }

        return String.format(
            "{\"health\":%.2f,\"maxHealth\":%.2f,\"inventory\":%s}",
            core.health,
            core.maxHealth,
            inventory
        );
    }

    /**
     * Escapes player-provided text before embedding it in the JSON response.
     *
     * Before:
     * - A name containing a quote or newline
     *
     * After:
     * - A valid JSON string value
     */
    private String escapeJson(String value) {
        final StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20)
                        escaped.append(String.format("\\u%04x", (int) character));
                    else
                        escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }
}
