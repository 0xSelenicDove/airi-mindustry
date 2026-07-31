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

- Design an allowlist of actions and a per-action confirmation flow.
- Add rate limits, an emergency stop, and audit logging.
- Remain local/private-server only until multi-user permissions are designed.

## Non-goals for the first release

- Autonomous construction or combat.
- Connecting to public or third-party servers.
- Sending game state to external services by default.
