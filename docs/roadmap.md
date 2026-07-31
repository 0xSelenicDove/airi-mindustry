# Roadmap

## Milestone 0: read-only local snapshot

- Run a local Mindustry headless server.
- Load a plugin that returns game status from `GET /v1/state`.
- Validate the response in the TypeScript bridge.
- Use the result only for conversational context.

## Milestone 1: AIRI extension adapter

- Define the extension manifest and configuration UI in AIRI.
- Present an explicit connection state and clear error messages.
- Convert snapshots into context, never direct model instructions.

## Milestone 2: user-approved actions

- Design an allowlist of actions and a per-action confirmation flow.
- Add rate limits, an emergency stop, and audit logging.
- Remain local/private-server only until multi-user permissions are designed.

## Non-goals for the first release

- Autonomous construction or combat.
- Connecting to public or third-party servers.
- Sending game state to external services by default.
