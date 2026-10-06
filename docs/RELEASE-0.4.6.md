# Finance Suite 0.4.6 — errores de base de datos

Base: 0.4.5, commit 5270d575a41a8b925e8031413f5f5303db90b0f2.

DatabaseErrorTracing intercepta los métodos públicos de JpaLedgerAdapter y
JpaSettingsAdapter para capturar DataAccessException, TransactionException y
PersistenceException. Se ejecuta antes del interceptor transaccional y relanza la
misma excepción: mantiene rollback y captura también errores de flush/commit.
Cubre operaciones del servidor invocadas desde REST y MCP.

Los logs ERROR incluyen operación, clase de excepción, código de diagnóstico,
SQLState y código del proveedor si hay SQLException en la cadena de causas,
correlationId y stacktrace completo con sus causas. No registra los argumentos
del método. Los mensajes del controlador JDBC pueden contener SQL o valores:
estas trazas técnicas deben tener acceso restringido.

ApiErrors captura también fallos de transacción que aparecen al cerrar una
transacción exterior del controlador. Clasifica las respuestas REST:

| Caso | HTTP | Código |
|---|---|---|
| Restricción de integridad | 409 | DATABASE_CONSTRAINT |
| Actualización concurrente optimista | 409 | DATABASE_CONCURRENT_UPDATE |
| Conexión, timeout o bloqueo temporal | 503 | DATABASE_UNAVAILABLE |
| Otros errores de persistencia | 500 | DATABASE_ERROR |

El ProblemDetail contiene code y correlationId (si existe en MDC), sin mensajes
SQL ni stacktrace. X-Correlation-ID permite relacionar la respuesta con las trazas.
No se reintentan ni se silencian operaciones automáticamente. Un error al confirmar
la transacción no equivale a una confirmación de importación.

Dependencia añadida: spring-boot-starter-aop. No hay cambios de tablas ni migraciones.

Validación Java 21: 46 pruebas del servidor, 45 correctas y una omitida
(MySQL/Testcontainers sin Docker). Pruebas nuevas: clasificación, diagnóstico SQL,
respuesta sin datos internos, conservación de la excepción y trazado del error de commit.
