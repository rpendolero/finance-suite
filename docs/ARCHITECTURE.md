# Arquitectura 0.4.0

Los tres módulos usan nombres funcionales y el groupId Maven `com.finance`:

| Módulo | Paquete | Responsabilidad |
|---|---|---|
| finance-domain | `com.finance.domain` | Entidades, invariantes y resultados comunes |
| finance-server | `com.finance.server.application.service` | Casos de uso y cálculos financieros |
| finance-server | `com.finance.server.application.port` | Contratos de consulta, persistencia y lectura de archivos |
| finance-server | `com.finance.server.application.model` | Resultados de análisis extendidos |
| finance-server | `com.finance.server.infrastructure.adapter` | REST, MCP, CSV y JPA |
| finance-server | `com.finance.server.infrastructure.config` | Beans, seguridad y filtros |
| finance-importer | `com.finance.importer.application.service` | Orquestación de una importación |
| finance-importer | `com.finance.importer.application.port` | Descarga, preparación y envío |
| finance-importer | `com.finance.importer.infrastructure.adapter` | CLI, HTTP, Excel/CSV y Playwright |
| finance-importer | `com.finance.importer.infrastructure.config` | Propiedades y conexión de implementaciones |

## Servicios del servidor

`FinanceService` coordina consultas; los cálculos corresponden a
`FinancialSummaryService`, `MerchantPatternService`, `CardExposureService`,
`LiquidityForecastService` y `DataQualityService`.

`ExtendedAnalysisService` coordina `MonthlyTrendService`,
`MerchantSpendingService`, `BudgetAnalysisService`, `ReconciliationService`,
`RecurringIncreaseService` y `CashFlowService`.

`ImportService` orquesta validación del producto, lectura, clasificación y
persistencia. `MovementClassificationService` aplica las reglas conservando
las categorías ya importadas. La creación de servicios y adaptadores se
centraliza en configuración; los constructores abreviados se conservan para
el uso directo sin Spring en las pruebas.

## Importador: responsabilidades y extensión

- `ImporterJobRunner`: selección de trabajo, snapshot y salida de consola.
- `ImporterService`: obtener archivo, prepararlo, enviarlo y limpiar tras confirmación.
- `NativeXlsStatementPreparationAdapter`: ciclo de vida del Excel y escritura del CSV.
- `NativeStatementFormat`: contrato de estrategia; cuentas y crédito tienen implementaciones separadas.
- `PlaywrightBankAdapter`: ciclo de vida del navegador y selección de recorrido.
- `BankExportFlow`: estrategias ING cuenta, ING crédito y CSV configurable.
- `BankUrlPolicy`: validación de dominios bancarios HTTPS.
- `PrivateBrowserStorage`: permisos de perfiles y almacenamiento de descargas.
- `HttpIngestionAdapter`: petición, autenticación y respuesta HTTP.
- `MultipartBatchEncoder`: codificación del lote multipart.

Para añadir un formato ING, implementa `NativeStatementFormat` y registra la
estrategia en `ImporterConfig`. Para añadir un recorrido, implementa
`BankExportFlow` y regístralo allí. No es necesario modificar los algoritmos
compartidos de lectura del Excel ni el ciclo de vida de Playwright.

## Principios y límites comprobables

Responsabilidad única: cada cálculo, formato, recorrido y transporte tiene un
propósito definido. Extensión: formatos y recorridos se seleccionan mediante
contratos de estrategia. Los puertos mantienen contratos pequeños de descarga,
preparación, envío, lectura y persistencia; la aplicación no conoce POI,
Playwright, JPA, JDBC, HTTP, Jackson ni Spring. Las implementaciones se conectan desde
infraestructura mediante inyección de constructor.

ArchUnit verifica la independencia del dominio y aplicación, ubicación de
servicios/adaptadores/configuración, ausencia de ciclos entre capas y separación
de procesos: el servidor no contiene navegador; el importador no contiene BBDD.
Las pruebas de comportamiento verifican conservación de compras repetidas,
rechazo de formatos/estados inválidos y limpieza solo tras envío confirmado.

## Migración

Paquetes Java y groupId cambian; las rutas REST, nombres MCP, esquema MySQL,
configuración `finance.*` y formato normalizado se conservan. El nombre interno
`CsvPreparationPort` pasa a `StatementPreparationPort` porque prepara tanto CSV
como XLS. Los launchers de las pruebas/documentación usan los nuevos paquetes.

Compila desde cero con `mvn clean install` para retirar clases generadas antiguas.
No es necesario reinstalar la base de datos ni regenerar perfiles de navegador.
La conversión de ambos Excel reales de ING produce el mismo CSV que 0.2.2,
incluidos los identificadores. No se incluyen archivos bancarios en el ZIP.

La navegación bancaria en vivo y los formatos pendientes de Kutxabank/PayPal
mantienen las limitaciones documentadas. La reorganización no añade conexiones
bancarias verificadas nuevas.

## Persistencia JPA

`JpaLedgerAdapter` y `JpaSettingsAdapter` implementan los puertos existentes con EntityManager y JPQL. Las entidades y la clave compuesta BudgetId residen en `infrastructure.adapter.out.persistence.entity`; PersistenceMapper convierte entre entidades y records del dominio con MapStruct. Lombok genera accesores y constructores sin argumentos de las entidades. No se generan toString ni equals sobre datos bancarios.

Flyway sigue siendo propietario del esquema existente. Hibernate arranca con `ddl-auto=validate`, `open-in-view=false` y zona JDBC UTC. Las transacciones se delimitan en adaptadores y en el caso de ingestión que incluye snapshot y movimientos. La deduplicación conserva la restricción única producto/external_id; las importaciones bloquean el producto con PESSIMISTIC_WRITE y adquieren múltiples bloqueos en orden de identificador. Los errores revierten el lote.

Las referencias a productos se mantienen como identificadores en las entidades; las claves foráneas existentes las protege MySQL. No se cargan asociaciones implícitas ni se exponen entidades desde REST/MCP. Las operaciones de borrado eliminan movimientos y producto dentro de una transacción.

Se retiraron los adaptadores JdbcTemplate del código de producción. JDBC sigue apareciendo en la URL de conexión y en comprobaciones independientes de las pruebas. No cambian las tablas, los endpoints ni la configuración de conexión.
