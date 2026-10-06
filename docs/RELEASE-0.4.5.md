# Finance Suite 0.4.5 — Spring Data JPA

Base: 0.4.4, commit e4bc44d3cec42b5cec15b04c6d282e52eb011e3f.

Todos los accesos a datos del servidor pasan por cuatro interfaces JpaRepository:
ProductRepository, MovementRepository, ClassificationRuleRepository y BudgetRepository.
JpaLedgerAdapter y JpaSettingsAdapter conservan los puertos de aplicación y MapStruct;
EntityManager deja de utilizarse directamente. El dominio y la aplicación no dependen
de Spring Data. La dependencia spring-boot-starter-data-jpa ya estaba configurada.

Consultas derivadas: productos ordenados por ID; movimientos por producto y fechas
inclusivas, ordenados por fecha e ID; deduplicación por producto y referencia bancaria;
reglas por prioridad e ID; presupuestos por mes y categoría; referencia única por banco.

ProductRepository usa @Lock(PESSIMISTIC_WRITE) para serializar las importaciones de
cada producto. Los bloqueos se adquieren por ID ordenado para evitar invertir el orden
entre lotes. Las transacciones mantienen rollback del lote, promoción PENDING a BOOKED,
validación de identidad, conservación de metadatos y borrado de movimientos antes del
producto. MovementRepository incluye una consulta de borrado con @Modifying y flush.

No requiere migración nueva ni cambios en los endpoints o en la configuración del
importador. Incluye los cambios de identificación de tarjetas de 0.4.4.

Pruebas añadidas: deduplicación dentro de un mismo lote con promoción de estado;
límites inclusivos y orden de consultas; reglas de arquitectura que impiden EntityManager
en persistencia y sitúan JpaRepository en infraestructura.

Validación con Java 21: `mvn -pl finance-server -am test` correcto.
42 pruebas: 41 correctas y 1 MySQL/Testcontainers omitida por ausencia de Docker.
Sin nuevas migraciones; comprobadas las pruebas de REST, ingestión, JPA y arquitectura.
