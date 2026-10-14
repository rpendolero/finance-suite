# MCP API key (read-only)

The MCP HTTP endpoint accepts `X-API-Key` with `ROLE_READER`. Browser login and session cookies are unchanged. The key cannot grant ADMIN or IMPORTER privileges.

## Provision a key

```bash
umask 077
export FINANCE_MCP_API_KEY="$(openssl rand -hex 32)"
export FINANCE_MCP_API_KEY_SHA256="$(printf %s "$FINANCE_MCP_API_KEY" | sha256sum | cut -d' ' -f1)"
```

Set `FINANCE_MCP_API_KEY_SHA256` in the **server** container environment and restart it. Keep the original `FINANCE_MCP_API_KEY` only in the client environment, never in Git or Docker image layers.

## OpenCode

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "finance-suite": {
      "type": "remote",
      "url": "https://finances.myaihome.es/mcp",
      "enabled": true,
      "headers": {
        "X-API-Key": "{env:FINANCE_MCP_API_KEY}"
      }
    }
  }
}
```

The server uses Spring AI MCP Streamable HTTP transport. Test with `opencode mcp list`; a plain GET or curl request is not sufficient to verify MCP initialization.

## Security notes

- This is a **single-key environment-backed initial implementation**, not a MySQL-backed key registry.
- Rotate by generating a new key/hash, updating the server environment, restarting and updating clients.
- Keep the public endpoint behind HTTPS; never log request headers containing secrets.
- MCP tool methods requiring ADMIN remain inaccessible with this key.
- Existing authenticated browser sessions can still reach MCP using their session permissions.
