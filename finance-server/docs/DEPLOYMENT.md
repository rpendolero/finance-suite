# Despliegue entre máquinas

El servidor no necesita escritorio. El importador necesita escritorio Linux y navegador en el ordenador del usuario. OpenClaw puede estar en otra máquina.

## HTTPS

Dos opciones:

1. Mantener Java en loopback:8081 y poner un proxy HTTPS existente delante. El proxy debe permitir POST/GET/DELETE `/mcp`, POST multipart `/api/importer/`, transmitir Authorization y las cabeceras MCP y permitir streaming SSE sin buffering; ajustar timeouts según el entorno. No publiques MySQL.
2. TLS directamente en Spring Boot, usando `config/tls-example.yml`, un certificado válido para el DNS del servidor y un almacén PKCS12:

```bash
# Desde finance-server, con TLS_KEY_STORE y TLS_KEY_STORE_PASSWORD configurados en .env:
scripts/start-server.sh --spring.config.additional-location=file:./config/tls-example.yml
```

`TLS_KEY_STORE=file:/ruta/absoluta/certificado.p12`. No se incluye un certificado ni se provisiona DNS/HTTPS: debes usar el de tu infraestructura. En modo contenedor, monta el certificado y la configuración TLS y publica el puerto 8443; compose por defecto solo publica HTTP en loopback y necesita esa adaptación explícita. Otra opción es un proxy en el mismo servidor con acceso al puerto local.

Importador: `FINANCE_SERVER_URL=https://nombre-real-del-servidor:8443`. OpenClaw: `FINANCE_MCP_URL=https://nombre-real-del-servidor:8443/mcp`.

El certificado debe ser confiable en los runtimes de Java del importador y Node del puente, con el DNS correcto. Si usas una CA privada, instala esa CA en sus almacenes de confianza; no se desactiva la verificación TLS. El importador no sigue redirecciones para proteger su credencial. Configura la URL definitiva, sin redirecciones HTTP→HTTPS ni a un login web del proxy.

No configures una URL con usuario/clave incluidos. Mantén los tres secretos separados: la máquina de importación solo necesita importer; OpenClaw solo reader; administración conserva admin. La credencial importer permite escribir datos financieros, por lo que debe protegerse y rotarse como cualquier credencial del servicio.

## Actualización desde el ordenador

En esta versión inicia el trabajo desde la CLI local. El servidor no da órdenes al ordenador ni mantiene una cola de trabajos. Si el ordenador está apagado no se actualizan datos, pero las consultas siguen disponibles. No se necesita escritorio remoto ni abrir un puerto local para recibir peticiones.

MySQL conserva los datos entre versiones: usa el mismo volumen, DB_URL y secretos, realiza copia de seguridad antes de desplegar y no borres el volumen. Las migraciones V1..V3 se conservan sin cambios. El antiguo endpoint browser-imports deja de existir. El servidor antiguo debe detenerse antes de ocupar su puerto con el nuevo.
