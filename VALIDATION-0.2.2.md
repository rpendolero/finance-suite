# Validación 0.2.2

Java 21: compilación directa de Product, puertos, ImporterService, adaptadores
CSV/ING y Playwright y sus propiedades. JUnit: 11 pruebas pasadas (4 de cuenta ING, 3 de tarjeta y
4 del servicio importador), incluida lectura de los XLS reales de 72 movimientos
de cuenta y 3 compras de crédito.
El archivo bancario real se usó solo localmente, no se distribuye.

No se completó `mvn verify` del reactor: la JVM no pudo acceder al repositorio
Maven del entorno. Las dependencias para la compilación y pruebas focalizadas
se descargaron por una vía disponible y las pruebas se ejecutaron con JUnit.
No se afirma que la suite completa, MySQL o una sesión real ING hayan pasado.
Ejecutar en el equipo destino: `mvn clean verify`.

La navegación se basa en la grabación del usuario, omite filtros de fechas
no verificados y conserva login/2FA manuales. Falta probarla con ING en vivo.
