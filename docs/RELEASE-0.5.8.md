# Finance Suite 0.5.8

## Cambios

- Autenticación de MCP mediante cabecera `X-API-Key`, validada contra el hash SHA-256 configurado en `FINANCE_MCP_API_KEY_SHA256`.
- Claves MCP limitadas a `ROLE_READER`; el login web basado en sesión se mantiene.
- Documentación para OpenClaw 2026.9.5 y OpenCode en `finance-server/docs/`.
- Versiones Maven y npm sincronizadas a 0.5.8.

## Despliegue

Genera una clave aleatoria de al menos 32 caracteres; guarda su hash SHA-256 en el entorno del servidor y la clave original en el cliente. No compartas secretos en Git.

## Limitaciones

La clave MCP es única y se configura por variable de entorno; no existe gestión de claves individuales en MySQL. La integración no ha sido validada mediante compilación ni pruebas de conexión en el despliegue real.
