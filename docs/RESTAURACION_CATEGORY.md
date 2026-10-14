# Categoría independiente «Restauración»

Versión integrada: `0.5.10` en `master`. Rama de desarrollo: `feature/restauracion-independiente`.

## Catálogo resultante

- `ALIMENTACION` (Alimentación): `SUPERMERCADO`, `OTROS_ALIMENTOS`.
- `RESTAURACION` (Restauración): `RESTAURANTES`, `BARES_CAFETERIAS`, `COMIDA_DOMICILIO`.
- `OCIO` conserva sus subcategorías, incluidos hoteles y viajes.

La migración `V12__restaurant_category_separation.sql` se ejecuta automáticamente por Flyway al
actualizar el servidor. No modificar el fichero `V6` de instalaciones existentes.

La migración:
1. crea o reactiva `RESTAURACION`;
2. **mueve** las subcategorías existentes `RESTAURANTES`, `COMIDA_DOMICILIO` y el código histórico opcional `RESTAURANTE`, conservando sus IDs y sin dejar copias inactivas en Alimentación;
3. crea Bares y cafeterías y Otros alimentos (no clasifica automáticamente movimientos ambiguos);
4. actualiza solamente los movimientos y reglas clasificados como
   `ALIMENTACION / RESTAURANTES`, `ALIMENTACION / RESTAURANTE` o `ALIMENTACION / COMIDA_DOMICILIO`;
5. no modifica importes, tipos computables/no computables, fuente ni confianza de clasificación.

Si ya existe una categoría Restauración creada manualmente y además una subcategoría
con el mismo código en ambas categorías, la restricción de unicidad detendrá la migración.
Resolver esta situación en la base de datos antes de desplegar; realizar una copia de seguridad.

El alias histórico `RESTAURANTS` pasa a normalizarse como `RESTAURACION`. Los nombres de
las subcategorías se envían mediante `subcategoryLabels` (campo adicional, compatible con
clientes existentes), para que el selector de clasificación muestre «Bares y cafeterías».

## Validación

Con la rama desplegada y una copia de seguridad de MySQL:

```bash
mvn -pl finance-server -am -Dtest=RestaurantCategoryMigrationTest,RestaurantCategoryCatalogTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Comprobar mediante SQL:

```sql
SELECT c.code AS category, s.code AS subcategory, s.name
FROM subcategory s JOIN category c ON c.id = s.category_id
WHERE c.code IN ('ALIMENTACION', 'RESTAURACION')
ORDER BY c.display_order, s.display_order;

SELECT category, subcategory, COUNT(*) AS movements, SUM(amount) AS total
FROM movement
WHERE category IN ('ALIMENTACION', 'RESTAURACION')
GROUP BY category, subcategory
ORDER BY category, subcategory;

SELECT category, subcategory, COUNT(*) AS rules
FROM classification_rule
WHERE category IN ('ALIMENTACION', 'RESTAURACION')
GROUP BY category, subcategory;
```

En el dashboard, comprobar el desglose de gastos en Restauración y que el editor de
clasificación permite asignar las tres subcategorías con sus etiquetas legibles.
El total de gastos del período no debe variar al trasladar categorías (solo su desglose).
