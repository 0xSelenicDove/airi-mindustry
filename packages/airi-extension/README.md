# AIRI Mindustry extension

This package registers AIRI tools for local Mindustry context, alerts, factory
reports, blueprint suggestions, repair-task status, and action audit history.

It also provides a deliberately narrow companion-control flow:

1. `prepare_mindustry_action` records an immutable proposal locally; it does
   not change the game.
2. AIRI presents its proposal ID to the player.
3. Only an explicit player request naming that ID can use
   `confirm_mindustry_action`. The proposal is one-time and expires after 180
   game ticks. The Mindustry mod still performs the authoritative preflight.
4. `stop_mindustry_actions` is available for explicit emergency stops, and
   `cancel_mindustry_action` discards a pending proposal without submission.

It requests the local bridge only when AIRI invokes the tool. The default endpoint is `http://127.0.0.1:18232/v1/context`; override it with `AIRI_MINDUSTRY_CONTEXT_URL` when launching AIRI.

## Install for development

1. Start the Mindustry plugin and bridge service:

   ```sh
   pnpm --dir packages/bridge serve
   ```

2. Copy or symlink this directory into AIRI Desktop's extension directory:

   ```text
   <AIRI user-data>/extensions/v1/airi-mindustry/
   ```

3. Reload extensions from AIRI's Plugin Host devtools page.

The extension is unavailable while the local bridge is offline. All requests
stay on loopback: the Bridge on port 18232 proxies authoritative action,
repair-status, and audit requests to the Mindustry plugin on port 18231.
