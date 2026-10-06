# Validación 0.4.0

Java 21 y Maven 3.9.9. Reactor completo compilado y empaquetado mediante `clean verify`, incluyendo las cuatro muestras privadas de ING y Kutxabank.

| Módulo | Pruebas contabilizadas | Superadas | Omitidas | Fallos/errores |
|---|---:|---:|---:|---:|
| finance-server | 33 | 32 | 1 | 0 |
| finance-importer | 31 | 31 | 0 | 0 |
| Total | 64 | 63 | 1 | 0 |

JPA/Hibernate con H2: productos y proveedores, reglas, presupuestos con clave compuesta, importación idempotente, promoción PENDING a BOOKED y clasificación. Una prueba ejecuta una transacción real independiente para verificar que un conflicto revierte el lote entero, aunque ya se haya persistido una fila anterior, y comprueba el borrado de producto y movimientos.

Las pruebas REST verifican atomicidad de snapshot y extracto, rechazo de snapshots antiguos y permisos. Las reglas ArchUnit impiden dependencias JPA/Hibernate en dominio y aplicación. Se mantienen pruebas de cálculos, CSV y contexto de trazas.

La prueba MySQL/Testcontainers se adaptó al nuevo adaptador JPA y arranque con Flyway y validación de esquema. Se omitió porque no hay Docker disponible: no se ha confirmado aquí la ejecución contra MySQL real. El esquema Flyway y los tipos de columnas se mantienen; Hibernate está configurado con `ddl-auto=validate`, nunca update.

Importador: contratos HTTP, conservación y limpieza de archivos, formatos ING y Kutxabank, selección de estrategia por cabecera, validación de fechas y una sola descarga de tarjeta Kutxabank. Se convirtieron las cuatro muestras reales sin incluirlas en el ZIP. La navegación bancaria sigue pendiente de comprobación en sesiones autenticadas reales.

Para reproducir: `mvn clean verify`. Las cuatro pruebas opcionales de muestras se omiten sin `-Ding.sample`, `-Ding.card.sample`, `-Dkutxabank.sample` y `-Dkutxabank.card.sample`; los escenarios sintéticos permanecen activos.
