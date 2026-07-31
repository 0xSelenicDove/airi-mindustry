# AIRI Mindustry extension

This package registers one read-only AIRI tool: `get_mindustry_context`.

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

The extension is unavailable while the local bridge is offline. It has no write or game-control operations.
