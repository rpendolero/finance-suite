# Enable Banking desde el Dashboard

Accede como `admin` y abre **Bancos**. Los otros roles no pueden gestionar conexiones.

1. Selecciona el banco del catálogo de Enable Banking y pulsa **Conectar banco**.
2. Completa la autorización en el banco. El callback vuelve a la sección Bancos.
3. Selecciona la conexión activa y pulsa **Descubrir cuentas**.
4. Selecciona para cada cuenta un producto existente y pulsa **Guardar vínculo**.
5. Pulsa **Sincronizar movimientos**. Se muestran leídos, insertados y duplicados.

Las conexiones guardadas se recuperan al volver a entrar. La sincronización solo
procesa cuentas vinculadas. Una conexión caducada necesita una nueva autorización.
La importación manual y Playwright siguen disponibles. No hay sincronización
periódica automática en esta pantalla.

## Configuración del servidor

- `ENABLE_BANKING_ENABLED=true`
- `ENABLE_BANKING_APPLICATION_ID`: identificador de la aplicación.
- `ENABLE_BANKING_PRIVATE_KEY`: clave privada RSA PKCS#8; nunca subirla a GitHub.
- `ENABLE_BANKING_REDIRECT_URL`: callback registrado en Enable Banking, por ejemplo
  `https://finances.myaihome.es/api/v1/banking/enable-banking/callback`.
- `ENABLE_BANKING_FRONTEND_URL`: dirección del Dashboard tras el callback. Por defecto
  `/` para producción en el mismo dominio. En desarrollo, `http://localhost:5173/`.
- `ENABLE_BANKING_COUNTRY=ES`
- `ENABLE_BANKING_CONSENT_DAYS=90`

El callback debe ser accesible y coincidir con la URL registrada en el proveedor.
Sin activar la integración, Bancos muestra un aviso al recibir 404. El listado de
conexiones nunca devuelve el identificador de sesión del proveedor ni el estado de
autorización. No se han probado cuentas bancarias reales en este entorno.
