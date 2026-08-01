# AIRI Mindustry

A local Mindustry integration for Project AIRI.

AIRI Mindustry connects the Mindustry desktop client with AIRI through a local, privacy-focused bridge. The current milestone is intentionally **read-only**: AIRI can receive validated game information for context and reasoning, but it cannot directly control the game, place blocks, control units, or perform autonomous actions.

## Current Status

🚧 Early development prototype

Implemented:

* Mindustry client mod
* Local snapshot API
* TypeScript bridge
* Snapshot validation
* Local game-data catalog
* Read-only context and event reporting

Not implemented:

* Autonomous building
* Autonomous combat
* Unit control
* Public server integration
* External game-state sharing by default

See [`docs/roadmap.md`](docs/roadmap.md) for planned milestones and safety boundaries.

---

# Architecture

```
Mindustry Desktop Client
        |
        v
AIRI Mindustry Client Mod
(loopback HTTP API)
        |
        v
TypeScript Bridge
(validation + processing)
        |
        v
Future AIRI Extension
```

The project is split into several packages:

## `packages/mindustry-plugin`

Java Mindustry client mod.

Responsibilities:

* Reads local game state
* Creates bounded snapshots
* Provides local HTTP endpoints
* Exposes only localhost services

The mod intentionally does not provide control actions.

## `packages/bridge`

TypeScript bridge layer.

Responsibilities:

* Fetches snapshots from the Mindustry mod
* Validates incoming data
* Produces AIRI-ready context
* Detects local events
* Provides deterministic suggestions

## `packages/airi-extension`

AIRI integration layer.

Responsible for connecting the validated bridge data to AIRI tools.

## `game-data`

Offline Mindustry data used for:

* block information
* recipes
* unit information
* construction requirements
* reviewed blueprints

---

# Quick Start

## Requirements

Install:

* Java Development Kit (JDK) 17+
* Node.js
* pnpm
* Mindustry desktop client

---

# Build the Mindustry Mod

From the repository root:

```bash
cd packages/mindustry-plugin
gradle jar
```

The generated file will be:

```
packages/mindustry-plugin/build/libs/airi-mindustry-plugin.jar
```

Copy this `.jar` file into your Mindustry mods folder.

Example locations:

Linux:

```
~/.local/share/Mindustry/mods/
```

macOS:

```
~/Library/Application Support/Mindustry/mods/
```

Restart Mindustry after adding the mod.

---

# Run the Bridge

Install dependencies:

```bash
pnpm install
```

Start the bridge:

```bash
pnpm --dir packages/bridge dev
```

To run the local HTTP listener:

```bash
pnpm --dir packages/bridge serve
```

Test:

```bash
curl http://127.0.0.1:18232/v1/context
```

The bridge only listens locally.

Do not expose these endpoints to a network without an authentication and permission design.

---

# Available Endpoints

## Context

```
GET /v1/context
```

Returns validated game context for AIRI.

Example:

```json
{
  "snapshotId": "example-id",
  "gameTick": 12345,
  "summary": "Current factory status..."
}
```

---

## Factory Report

```
GET /v1/factory-report
```

Provides aggregated information:

* power production
* power consumption
* batteries
* production buildings
* crafting activity

---

## Events

```
GET /v1/events
```

Reports detected changes such as:

* wave starts
* enemy pressure changes
* core damage
* power shortages
* low resources

---

## Suggestions

```
GET /v1/suggestions
```

Provides deterministic, non-executing suggestions.

Example:

* possible defense improvements
* reviewed blueprint recommendations

Suggestions are only proposals. They do not modify the game.

---

# Development

## Mindustry Mod

Build:

```bash
cd packages/mindustry-plugin
gradle jar
```

## Bridge

Install:

```bash
pnpm install
```

Build:

```bash
pnpm --dir packages/bridge build
```

Type check:

```bash
pnpm --dir packages/bridge type-check
```

Lint:

```bash
pnpm --dir packages/bridge lint
```

Tests:

```bash
pnpm --dir packages/bridge test
```

AI loop test:

```bash
pnpm --dir packages/bridge test:ai-loop
```

---

# Offline Game Catalog

Mindustry game data can be generated with:

```bash
gradle -p tools/generate-game-data run
```

Generated data includes:

* items
* liquids
* blocks
* recipes
* units
* wave information

The bridge loads this data locally for reliable lookups.

Reviewed blueprints can be added to:

```
game-data/v159/blueprints.json
```

The generator does not overwrite reviewed blueprints.

---

# Safety and Privacy

AIRI Mindustry is designed around local-first operation.

Current guarantees:

* The Mindustry mod only communicates through localhost.
* Game snapshots are validated before use.
* The bridge does not execute game actions.
* External model providers should not receive game data without user consent.

Future action systems must include:

* explicit user confirmation
* action allowlists
* snapshot validation
* rate limits
* emergency stop mechanisms

---

# Troubleshooting

## Mod does not appear in Mindustry

Check:

* The `.jar` file is inside the Mindustry mods folder.
* Mindustry was restarted after adding the mod.
* The Java version is compatible.

---

## Gradle cannot find Java compiler

Make sure you installed the JDK, not only the runtime:

Ubuntu:

```bash
sudo apt install openjdk-21-jdk
```

If Gradle still uses old information:

```bash
gradle --stop
```

Then rebuild.

---

## Bridge does not respond

Check that the server is running:

```bash
pnpm --dir packages/bridge serve
```

Then:

```bash
curl http://127.0.0.1:18232/v1/context
```

---

# Contributing

Contributions are welcome.

Good first contributions:

* documentation improvements
* tests
* bridge improvements
* game-data additions
* bug fixes

For larger features, especially anything involving game control or automation, please open an issue first so the design can be discussed.

Before submitting a pull request:

* explain what changed
* explain how to test it
* include relevant build/test results
* keep safety boundaries in mind

---

# Roadmap

See:

```
docs/roadmap.md
```

The planned direction is:

1. Read-only game awareness
2. AIRI context integration
3. User-approved actions

The project intentionally avoids uncontrolled automation.

---

# License

#Add project license information here.
