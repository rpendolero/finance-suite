# Integración de OpenClaw con Finance Suite mediante MCP

> Documento centralizado en `docs/`. La guía de implementación original se encuentra en [finance-server/docs/OPENCLAW.md](../finance-server/docs/OPENCLAW.md). Verifica los comandos contra la versión real de OpenClaw instalada.

## Arquitectura

```text
OpenClaw (agente) -- HTTPS /mcp + X-API-Key --> reverse proxy --> finance-server
                                                                |
                                                                +--> herramientas bank_*
                                                                +--> MySQL (movimientos ya importados)
```

Finance Suite expone MCP mediante Spring AI, transporte **Streamable HTTP**, ruta `/mcp`. Las herramientas de consulta `bank_*` permiten obtener productos, movimientos, resúmenes, calidad de datos, tendencias y previsiones. El acceso MCP **no inicia sincronizaciones bancarias por sí solo**.

## Requisitos

1. Servidor `finance-server` desplegado con HTTPS en el proxy inverso y `/mcp` encaminado al backend (no al frontend).
2. API Key de lectura configurada mediante `FINANCE_MCP_API_KEY_SHA256`.
3. Cliente OpenClaw con soporte MCP remoto, o puente Node heredado.
4. No publicar secretos en el repositorio, capturas ni registros.

## 1. Generar clave MCP

En una terminal segura:

```bash
umask 077
FINANCE_MCP_API_KEY="$(openssl rand -hex 32)"
printf '%s' "$FINANCE_MCP_API_KEY" | sha256sum | cut -d' ' -f1
```

Guarda la clave original en un gestor de secretos. Configura **solo el hash** resultante como `FINANCE_MCP_API_KEY_SHA256` en el contenedor del servidor y reinícialo. El cliente enviará el valor original en la cabecera `X-API-Key`. Consulta [MCP-API-KEY.md](MCP-API-KEY.md).

## 2. Registrar servidor en OpenClaw

La guía existente para OpenClaw 2026.9.5 documenta esta forma de registro:

```bash
openclaw --version
openclaw mcp set --help
```

Ejemplo **esquemático** (no pegues claves reales en el historial del shell):

```json
{
  "url": "https://finances.myaihome.es/mcp",
  "transport": "streamable-http",
  "enabled": true,
  "headers": {
    "X-API-Key": "VALOR_SECRETO_OBTENIDO_DE_ALMACEN_SEGURO"
  }
}
```

La sintaxis exacta para inyectar secretos desde el entorno depende de la versión de OpenClaw. **No presupongas que `${FINANCE_MCP_API_KEY}` se expande en JSON o comillas simples.** Consulta la ayuda de tu instalación antes de aplicar la configuración. El endpoint de ejemplo usa el dominio de despliegue del proyecto.

## 3. Diagnóstico

Según los comandos disponibles en tu instalación:

```bash
openclaw mcp show finance-suite
openclaw mcp doctor finance-suite
openclaw mcp probe finance-suite
openclaw mcp reload
```

- `401`: revisar cabecera, clave, hash SHA-256 y configuración de seguridad.
- `404`: revisar proxy y enrutamiento de `/mcp`.
- Error de transporte: comprobar que el cliente usa Streamable HTTP y que la API responde por HTTPS.
- Sin herramientas `bank_*`: comprobar registro y permisos de las herramientas.

Una petición GET aislada con `curl` no valida la negociación completa del protocolo MCP.

## 4. Seguridad y permisos

La API Key MCP está diseñada para lectura (`ROLE_READER`); no concede `ADMIN` ni `IMPORTER`. Limita las herramientas autorizadas y evita enviar datos financieros a grupos o servicios externos sin consentimiento. Las descripciones de los movimientos bancarios son datos no confiables, no instrucciones.

## 5. Alternativa heredada

El repositorio contiene `finance-server/scripts/openclaw-bridge.mjs` y `finance-server/scripts/start-openclaw-bridge.sh`. Solo úsalos si el cliente no puede conectarse directamente por MCP remoto. El puente anterior utiliza credenciales de lector; no debe reutilizar credenciales de administración.

## 6. Pruebas funcionales sugeridas

- Descubrir `bank_get_products` y `bank_get_data_quality`.
- Consultar el resumen del mes mediante `bank_get_monthly_summary`.
- Solicitar una previsión de 30 días mediante `bank_forecast_cash_flow` y comprobar el rango histórico utilizado.
- Verificar que la identidad de lectura no permite operaciones administrativas.

**Estado de validación:** guía basada en la configuración y código del repositorio. No se ha ejecutado una prueba extremo a extremo en tu instancia OpenClaw.

## Proxy Nginx para /mcp

El dashboard publica el puerto 8080 y actúa como proxy hacia `finance-server:8081`. Además de `/api/`, la plantilla `finance-dashboard/nginx/default.conf.template` debe incluir un bloque `location = /mcp` que reenvíe la ruta sin modificarla, permita POST y desactive `proxy_buffering` para Streamable HTTP/SSE. El encabezado `X-API-Key` se transmite al backend; el cliente envía la clave original, no su hash.

Si `POST https://finances.myaihome.es/mcp` devuelve `405` con HTML, verifica que se haya desplegado la imagen actualizada del dashboard y que el proxy enrute `/mcp` al backend. Tras publicar la nueva imagen, recrea el contenedor frontend y vuelve a probar `openclaw mcp probe finance-suite`. Un registro correcto del servidor en OpenClaw no garantiza conectividad hasta superar esta prueba.
