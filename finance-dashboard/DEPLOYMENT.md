# Desplegar el frontend con Docker

El contenedor compila React con Node y sirve `dist` mediante Nginx. No incluye la API ni MySQL. La base de datos externa solo se configura en Spring Boot.

Desde la raíz del repositorio:

```bash
docker build -t finance-dashboard -f finance-dashboard/Dockerfile finance-dashboard
docker run -d --name finance-dashboard --restart unless-stopped \
  -p 127.0.0.1:8080:80 \
  -e API_UPSTREAM=http://IP_SERVIDOR_API:8081 \
  finance-dashboard
```

Sustituye IP_SERVIDOR_API por una dirección accesible desde el contenedor. API_UPSTREAM debe ser una URL base sin ruta ni barra final, por ejemplo http://192.168.1.20:8081. Si la API está en otro contenedor, conecta ambos a una misma red Docker y usa su nombre: `--network finance` y `API_UPSTREAM=http://server:8081`. No uses localhost como destino: desde Nginx sería el propio contenedor.

Alternativa con Compose, desde finance-dashboard:

```bash
API_UPSTREAM=http://IP_SERVIDOR_API:8081 docker compose up -d --build
```

Por defecto publica en 127.0.0.1:8080 para el proxy inverso del servidor. Para acceder directamente desde la red local:

```bash
FRONTEND_BIND_ADDRESS=0.0.0.0 API_UPSTREAM=http://IP_SERVIDOR_API:8081 docker compose up -d --build
```

Si la API corre directamente en el mismo servidor, Compose añade host.docker.internal; usa `API_UPSTREAM=http://host.docker.internal:8081`. La API debe escuchar en una interfaz accesible desde Docker; una API publicada exclusivamente en 127.0.0.1 no se alcanza por esa dirección.

## URL pública, login y Enable Banking

Dirige el proxy HTTPS de https://finances.myaihome.es al frontend en http://127.0.0.1:8080, preservando Host y enviando X-Forwarded-Proto: https. Nginx sirve React y reenvía /api/ a la API, conservando la ruta, cookies y métodos. El proxy de Vite no interviene en producción.

Configura estas variables en el servidor API:

```env
FINANCE_ALLOWED_ORIGINS=https://finances.myaihome.es
ENABLE_BANKING_REDIRECT_URL=https://finances.myaihome.es/api/v1/banking/enable-banking/callback
ENABLE_BANKING_FRONTEND_URL=https://finances.myaihome.es/
```

Si accedes por una URL local distinta, añádela a FINANCE_ALLOWED_ORIGINS separada por coma. La autorización bancaria sigue necesitando el callback HTTPS registrado en Enable Banking.

## Comprobaciones

```bash
docker exec finance-dashboard nginx -t
curl -I http://127.0.0.1:8080/
curl http://127.0.0.1:8080/healthz
curl -i http://127.0.0.1:8080/api/auth/me
```

Sin sesión, /api/auth/me debe devolver una respuesta de autenticación de la API (normalmente 403), no HTML de React ni 502. Comprueba después el login desde el dominio público. `docker logs finance-dashboard` muestra los accesos y errores de Nginx.

Al cambiar el frontend, reconstruye la imagen. Al cambiar API_UPSTREAM, recrea el contenedor; la imagen oficial de Nginx procesa la plantilla al arrancar. No hace falta recompilar React para cambiar el destino de la API.
