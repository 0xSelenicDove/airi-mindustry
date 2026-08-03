# Roadmap

## Milestone 0: read-only local client snapshot

- Load a Mindustry desktop client Mod that returns the player's visible game state from `GET /v1/state`.
- Validate the response in the TypeScript bridge.
- Use the result only for conversational context.

## Milestone 1: AIRI extension adapter and companion awareness

- Register AIRI tools for current game context, a factory report, and newly detected local events.
- Report core, power-network, and production-building state through loopback-only endpoints.
- Detect wave starts, enemy pressure, core damage, power shortfalls, and low resources between snapshots.
- Convert snapshots into context, never direct model instructions or autonomous actions.

## Milestone 2: user-approved actions

- Implemented: an action allowlist and explicit confirmation flow for the vetted `PLACE_BLUEPRINT` and `REPAIR_ZONE` actions.
- Implemented: stale-world-state rejection (180 ticks), idempotency for repair submission, and host-authoritative Poly repair control.
- Implemented: player cancellation and manual Poly repurposing immediately release AIRI control; client-only multiplayer control is rejected.
- Remaining: expose repair task-status polling/events to the bridge and validate the full repair flow in a live host game.
- Remaining: add rate limits, emergency-stop/audit logging, and multi-user permissions before expanding beyond local/private-server use.

## Non-goals for the first release

- Autonomous construction or combat.
- Connecting to public or third-party servers.
- Sending game state to external services by default.
