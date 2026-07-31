# AIRI Mindustry

An early, local-client Mindustry integration for [Project AIRI](https://github.com/moeru-ai/airi).

The first milestone is deliberately read-only: AIRI can receive a concise snapshot from the player's Mindustry client, whether the player is in single-player or joined to a server. It cannot place blocks, control units, or connect to public servers.

## Architecture

```text
Mindustry desktop client
  └─ AIRI Mindustry client Mod (loopback HTTP, port 18231)
       └─ TypeScript bridge (validates and forwards snapshots)
            └─ future AIRI extension
```

## Packages

- `packages/mindustry-plugin`: Java client Mod. It exposes `GET /v1/state` only on `127.0.0.1`, including timestamped core, power, production, bounded zone, resource-limit, and alert aggregates.
- `packages/bridge`: TypeScript process that fetches and validates the snapshot. It currently prints JSON to stdout, which makes it easy to test before adding AIRI-specific wiring.
- `packages/airi-extension`: installable AIRI extension that registers the read-only `get_mindustry_context` tool.

## Run locally

1. Install Java 17 and the [Mindustry desktop client](https://mindustrygame.github.io/wiki/).
2. Build the client Mod:

   ```sh
   cd packages/mindustry-plugin
   gradle jar
   ```

3. Copy `build/libs/airi-mindustry-mod.jar` into Mindustry's `mods/` directory, then start or restart the client. On macOS, this is typically `~/Library/Application Support/Mindustry/mods/`.
4. Install the bridge dependencies and request a snapshot:

   ```sh
   pnpm install
   pnpm --dir packages/bridge dev
   ```

The client Mod intentionally binds to loopback. Do not expose its port to a network until an authentication and permission design exists.

To make the opt-in AIRI-ready context available locally, run:

```sh
pnpm --dir packages/bridge serve
curl http://127.0.0.1:18232/v1/context
```

This separate loopback listener is the stable boundary for a future AIRI extension: it returns the compact `summary` alongside the validated source snapshot.

The bridge also exposes two read-only companion endpoints:

- `GET /v1/factory-report`: power balance, battery state, and production/crafting building counts.
- `GET /v1/events`: changes detected since the bridge's previous snapshot, plus the conditions it actively monitors: wave starts, increasing enemy pressure, core damage, power shortfalls, and resources that have fallen to a low level.
- `GET /v1/suggestions`: deterministic, non-executing candidates for the reviewed `starter-duo-defense` blueprint when defense exposure is active and the core has at least 70 copper.

The v2 state response includes a unique `snapshotId` and authoritative `gameTick`, the active map, aggregate player/unit counts, default-team core health and inventory, power metrics, production/crafting structures, up to 12 coarse zone summaries, low-resource indicators, and deterministic alerts. It deliberately omits player names, raw tile data, and individual building coordinates. Treat all game state as local-session data: do not send it to an external model provider without the players' consent.

Future actions must include `refSnapshotId` and `refTick`. The client-side `ActionValidator` rejects actions more than 180 simulation ticks old with `STALE_WORLD_STATE`; no action endpoint is exposed yet.

The bridge also provides a local `ReasoningDebouncer` primitive for the future AIRI reasoning path: it permits one in-flight request per normalized alert/topic and has an explicit timeout cleanup mechanism. Real-time emergency detection remains in the Java mod; it must not depend on an LLM response.

## Reasoning resilience

The bridge's provider-neutral `ResilientReasoner` wraps streaming model clients with a 3.5-second `AbortController` timeout. Text chunks may be surfaced to the UI as they arrive; completed `NOTIFY_USER` advice is retained in local history even when its reference tick later becomes stale. It never promotes generated text to a game action.

`PLACE_BLUEPRINT` and other world-changing actions retain strict snapshot validation in the client mod. `prefetchStrategy` starts background reasoning (without awaiting it) when the next wave is less than 20 seconds away or battery reserve falls below 35%. The current repository has no model-provider adapter or action endpoint, so these primitives are intentionally not activated by the read-only bridge server yet.

## Offline game catalog

Run `gradle -p tools/generate-game-data run` to export the pinned `Mindustry-v159.7.jar` base content into `game-data/v159/`. This produces items, liquids, blocks with construction requirements, item/liquid recipes, units, and a generated-default wave profile. It runs headlessly and does not scrape a manual or load user mods.

`packages/bridge/src/knowledge/catalog.ts` loads those assets locally for exact block, recipe, and vetted blueprint lookups. Add only reviewed schematics to `game-data/v159/blueprints.json`; the generator never overwrites that file.

## Development status

See [docs/roadmap.md](docs/roadmap.md) for the proposed milestones and safety boundaries.
