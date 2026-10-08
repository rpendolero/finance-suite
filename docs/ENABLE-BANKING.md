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

## Adaptador tipado

El adaptador usa DTO del proveedor con Lombok y Jackson en el paquete `enablebanking.dto`.
`EnableBankingApiClient` centraliza HTTP y deserialización; `EnableBankingDataMapper`
convierte los datos a los objetos del puerto de aplicación. No se utilizan JsonNode
ni mapas para construir peticiones del API.

La consulta de sesión lee identificadores de texto en `accounts` y consulta los detalles
de cada cuenta para obtener nombre y moneda. Los IBAN usados como nombre se muestran
solo con sus cuatro últimas posiciones. La respuesta de creación de sesión tiene un
DTO distinto para no confundir su formato con el de consulta de sesión.

Los movimientos recorren las páginas `continuation_key`, incluso si una página está
vacía. Se respeta `credit_debit_indicator` y se unen las líneas de remittance_information.
Las respuestas incompletas provocan un error explícito. Los errores HTTP registran
estado y código del proveedor sin volcar cuerpos ni credenciales.
