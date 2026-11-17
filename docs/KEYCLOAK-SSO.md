# Finance Suite: Keycloak SSO compartido

La autenticación centralizada es **opcional**. Finance Suite conserva su
login por contraseña en el perfil por defecto y utiliza Keycloak exclusivamente
cuando se activa el perfil de Spring Boot `keycloak`. El servidor de
identidad, realm, PostgreSQL y clientes se definen en
`rpendolero/clima-home/keycloak/` (realm `myaihome`).

## Activación

Primero despliega Keycloak con el realm `myaihome`, configura el acceso
HTTPS y comprueba su documento OIDC:

```bash
curl -fsS https://auth.myaihome.es/realms/myaihome/.well-known/openid-configuration
```

El issuer del JSON debe coincidir exactamente con el configurado en
Finance Suite y ser alcanzable por el backend Docker.

Establece estas variables en el entorno del backend de Finance Suite:

```dotenv
SPRING_PROFILES_ACTIVE=keycloak
KEYCLOAK_ISSUER_URI=https://auth.myaihome.es/realms/myaihome
KEYCLOAK_CLIENT_ID=finance-suite
KEYCLOAK_CLIENT_SECRET=secret-de-FINANCE_CLIENT_SECRET-del-keycloak/.env
SESSION_COOKIE_SECURE=true
```

El secreto del cliente finance-suite debe coincidir con el registrado
en Keycloak. **No incluyas el secreto en imágenes, frontend ni Git.**
La configuración `finance-server/compose.yml` pasa estas variables al backend.
Conserva las variables locales `FINANCE_READER_PASSWORD`,
`FINANCE_IMPORTER_PASSWORD`, `FINANCE_ADMIN_PASSWORD` durante la migración;
el profile `keycloak` deja de registrar cuentas locales aunque estas variables
se sigan necesitando para la configuración actual.

Publica/despliega las imágenes actualizadas de frontend y backend.
El dashboard obtiene `GET /api/auth/config` para mostrar la entrada SSO
y usa `/oauth2/authorization/keycloak`; el proxy de Nginx reenvía
`/oauth2/` y `/login/oauth2/` a Spring Security.

## Roles independientes

En Keycloak → realm `myaihome` → Users → Role mapping, concede
roles de **cliente `finance-suite`**, nunca solo un rol de realm:

| Rol Keycloak | Funciones |
| --- | --- |
| `READER` | Consultar información financiera |
| `IMPORTER` | Importación a través de los endpoints protegidos de importador |
| `ADMIN` | Gestión y escritura; implica READER e IMPORTER |

El backend acepta exclusivamente `resource_access.finance-suite.roles`
del **ID token firmado y validado** por Spring Security. Los roles de
`clima-home` y `atlas-cloud` no autorizan consultar las finanzas.
La identidad recibida debe tener `email_verified=true`. Un usuario
recién creado mediante Google necesita una asignación explícita de rol:
se rechaza por defecto.

Las rutas MCP conservan su autenticación API Key preexistente y los
endpoints bancarios sus comprobaciones de ADMIN; no se ha abierto el
acceso anónimo a ningún endpoint financiero.

## Pruebas de aceptación

1. Acceso con usuario sin rol de Finance: denegado.
2. Usuario con `clima-home / ADMIN` únicamente: denegado.
3. Usuario con `finance-suite / READER`: puede consultar, no modificar.
4. Usuario con `finance-suite / ADMIN`: conserva permisos bancarios y de
   edición de movimientos.
5. Un login local `POST /api/auth/login` en modo Keycloak devuelve 403.
6. En modo `default`, el acceso local sigue funcionando.
7. Probar el callback de Enable Banking; esta integración OAuth bancaria
   no se ha modificado ni sustituido por Keycloak.

La aplicación mantiene su sesión `JSESSIONID` con cookie segura en HTTPS.
Cerrar sesión desde Finance Suite invalida la sesión local. **No realiza
todavía un cierre global de todas las sesiones en Keycloak**.
