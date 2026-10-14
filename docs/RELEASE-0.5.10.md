# Finance Suite 0.5.10

## Cambios

- Nueva categoría de gastos **Restauración** (`RESTAURACION`) independiente de **Alimentación**.
- Restauración incluye `RESTAURANTES`, `BARES_CAFETERIAS` y `COMIDA_DOMICILIO`;
  las instalaciones que ya usaban el código singular `RESTAURANTE` lo conservan al trasladarlo.
- Alimentación conserva `SUPERMERCADO` y añade `OTROS_ALIMENTOS`.
- Flyway **V12__restaurant_category_separation.sql** crea el nuevo catálogo y traspasa
  automáticamente los movimientos históricos y las reglas de clasificación inequívocas.
  No altera importes, tipos de movimiento, fuentes ni confianza de clasificación.
- Corrección de los alias de clasificación `RESTAURANTS`, `RESTAURANTE`,
  `RESTAURANTES`, `RESTAURANT` y `DINING` para que apunten a `RESTAURACION`.
- En el editor de movimientos, los selectores muestran nombres del catálogo (p. ej.
  «Bares y cafeterías») mediante el nuevo campo de respuesta opcional `subcategoryLabels`.
- Versiones Maven, importer, dashboard y puente MCP actualizadas a **0.5.10**.

## Migración y compatibilidad

- **Hacer una copia de seguridad de MySQL antes del despliegue.**
- La migración V12 se ejecuta automáticamente con Flyway al iniciar finance-server.
- No es preciso ejecutar la reclasificación masiva: solo se actualizan categorías
  inequívocas, y permanecen intactas las clasificaciones ambiguas.
- No cambia ninguna ruta REST ni la estructura de peticiones: `subcategoryLabels`
  es un campo **adicional** en la respuesta del catálogo.
- Revisar previamente posibles subcategorías personalizadas duplicadas entre
  Alimentación y Restauración, ya que pueden impedir mover una fila por la
  restricción `UNIQUE(category_id, code)`.
- Validación y consultas SQL: [Guía de restauración](RESTAURACION_CATEGORY.md).

## Pruebas

Se han añadido `RestaurantCategoryMigrationTest` y `RestaurantCategoryCatalogTest`
para verificar migración del histórico, metadatos, reglas, alias y catálogo.

Comando para ejecutarlas:

```bash
mvn -pl finance-server -am \
  -Dtest=RestaurantCategoryMigrationTest,RestaurantCategoryCatalogTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

La ejecución efectiva de estas pruebas y la validación sobre MySQL real deben
comprobarse antes de desplegar en producción.
