# AIRI Mindustry

An early, self-hosted Mindustry integration for [Project AIRI](https://github.com/moeru-ai/airi).

The first milestone is deliberately read-only: AIRI can receive a concise snapshot of a **locally hosted** Mindustry game. It cannot place blocks, control units, or connect to public servers.

## Architecture

```text
Mindustry headless server
  └─ AIRI Mindustry server plugin (loopback HTTP, port 18231)
       └─ TypeScript bridge (validates and forwards snapshots)
            └─ future AIRI extension
```

## Packages

- `packages/mindustry-plugin`: Java server plugin. It exposes `GET /v1/state` only on `127.0.0.1`.
- `packages/bridge`: TypeScript process that fetches and validates the snapshot. It currently prints JSON to stdout, which makes it easy to test before adding AIRI-specific wiring.

## Run locally

1. Install Java 17 and a local [Mindustry headless server](https://mindustrygame.github.io/wiki/servers/).
2. Build the plugin:

   ```sh
   cd packages/mindustry-plugin
   gradle jar
   ```

3. Copy `build/libs/airi-mindustry-plugin.jar` into the server's `config/mods/` directory, then start the server.
4. Install the bridge dependencies and request a snapshot:

   ```sh
   pnpm install
   pnpm --dir packages/bridge dev
   ```

The server plugin intentionally binds to loopback. Do not expose its port to a network until an authentication and permission design exists.

To make the opt-in AIRI-ready context available locally, run:

```sh
pnpm --dir packages/bridge serve
curl http://127.0.0.1:18232/v1/context
```

This separate loopback listener is the stable boundary for a future AIRI extension: it returns the compact `summary` alongside the validated source snapshot.

The state response includes the active map, connected player names, enemy count, and default-team core health and inventory. Treat player names and game state as local-session data: do not send them to an external model provider without the players' consent.

## Development status

See [docs/roadmap.md](docs/roadmap.md) for the proposed milestones and safety boundaries.
