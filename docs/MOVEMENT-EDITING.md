# Consulta y edición de movimientos

La pantalla **Movimientos** muestra los resultados en modo de consulta. Un administrador puede pulsar una fila o **Editar** para abrir el modal de la aplicación. El modal permite cambiar categoría, subcategoría y tratamiento, incluyendo **No computable**. **Guardar** modifica ese movimiento; **Cancelar**, Escape o pulsar fuera descartan los cambios pendientes. Durante el guardado no se permite cerrar el modal.

La acción **Aplicar al comercio** conserva su alcance anterior: modifica también el histórico de ese comercio y guarda una regla. **Eliminar** está dentro del modal y mantiene su confirmación independiente. Los usuarios lectores sólo pueden consultar.

## Selección y edición masiva

1. Buscar usando los filtros habituales.
2. Marcar movimientos individuales o **Seleccionar esta página**.
3. Si hay más resultados, usar **Seleccionar los N resultados de la búsqueda** para incluir otras páginas. El contador muestra siempre el alcance exacto; se pueden desmarcar movimientos concretos.
4. Pulsar **Recategorizar seleccionados (N)** y elegir categoría, subcategoría y tratamiento.
5. Confirmar con **Aplicar a N movimientos**.

La selección se conserva al cambiar de página o de tamaño de página. Cambiar cualquier filtro o ejecutar otra búsqueda limpia la selección. Mientras hay filtros pendientes de aplicar, los resultados anteriores no se pueden seleccionar ni editar.

La selección completa se obtiene recorriendo la búsqueda antes de editar: se envían los identificadores concretos seleccionados, nunca una regla por comercio ni un filtro abierto que pueda incorporar movimientos nuevos durante el guardado. Si el total cambia durante la selección, se solicita repetir la búsqueda. Los errores mantienen el modal y la selección para corregir o reintentar.

El listado ofrece **25, 50 o 100 movimientos por página**, inicialmente 25. La preferencia se recuerda en este navegador. Guardar mantiene filtros, página y posición; si la última página queda vacía por la recategorización, se carga la última página válida.

## Ingresos y gastos

Los movimientos de cada categoría usan el mismo componente de listado que la búsqueda: filas de consulta, edición mediante modal al pulsar la fila o **Editar**, selección masiva y páginas de **25, 50 o 100**. La preferencia del tamaño de página se comparte entre ambas pantallas. Las acciones de guardar, cancelar, aplicar al comercio y eliminar tienen el mismo comportamiento y permisos.

**Seleccionar los N movimientos de esta categoría** recorre todas sus páginas y se limita a la categoría abierta, al período y a la vista de ingresos o gastos seleccionados. Cambiar de categoría, período o tipo de análisis limpia la selección, incluyendo selecciones aún en carga. Cambiar de página o de tamaño conserva la selección.

Al guardar se actualizan el detalle, las categorías y los totales. Se mantiene la categoría y la página si siguen existiendo; una última página vacía retrocede a la última válida. Si la categoría queda sin movimientos, se cierra y desaparece del resumen, conservando el mensaje de confirmación.

## API

`PATCH /api/movements/classification` requiere el rol `ADMIN` y la sesión habitual:

```json
{
  "movementIds": ["id-1", "id-2"],
  "category": "RESTAURACION",
  "subcategory": "RESTAURANTE",
  "kind": "NORMAL"
}
```

Respuesta: `{"updated":2}`. Valida el catálogo, todos los identificadores y el tratamiento antes de escribir. La persistencia del lote usa una única transacción: un error revierte el conjunto. Los identificadores repetidos se procesan una vez. No crea reglas ni modifica movimientos fuera de la selección. Al aplicar `NORMAL`, las devoluciones existentes siguen siendo devoluciones. `REFUND` sólo es válido para importes positivos; `NO_COMPUTABLE` necesita una subcategoría válida.

Es necesario desplegar **frontend y servidor de esta rama** para usar la edición masiva. No hay migraciones de base de datos.

## Coherencia de los importes

Calendario, evolución, resumen, ingresos y gastos, categorías, productos y el detalle de movimientos computables usan el mismo criterio:

- Sólo movimientos contabilizados (`BOOKED`), con tratamiento `NORMAL` o `REFUND`, y categoría distinta de `NO_COMPUTABLE`.
- Los importes negativos son gastos, incluidos los cargos de financiación y ahorro/inversión.
- Los importes positivos son ingresos, incluidas las devoluciones y los reembolsos de inversión.
- Balance y flujo disponible = ingresos − gastos. Los desgloses netos de ahorro y financiación son informativos y no se vuelven a restar.
- Los pendientes, duplicados, transferencias internas y liquidaciones no computables quedan excluidos de importes y contadores.

El listado general de búsqueda puede mostrar también movimientos excluidos para poder revisarlos. El modal **Movimientos computables** aplica los filtros anteriores y recorre todas las páginas de la fecha/producto seleccionado. Sus totales se suman en céntimos.

Caso de regresión: cargos ordinarios de 251,16 € y cuota de vehículo de 203,95 € deben producir **455,11 € de gastos** en todas las vistas. La corrección requiere actualizar tanto servidor como frontend, sin reclasificar ni modificar movimientos existentes.

## Inicio y previsión

La evolución financiera se mantiene en Inicio; se elimina la pantalla independiente y su entrada del menú.

El panel **Previsión de saldo** utiliza los campos reales de `/api/analysis/forecast`: saldo de partida (`accountBalances`), flujo neto estimado (`estimatedNetFlow`) y saldo estimado (`estimatedAccountBalance`), junto al horizonte (`days`) y fecha del cálculo (`asOf`). Antes consultaba campos de gastos que ese endpoint no devuelve y mostraba erróneamente que no había proyección.

Se muestran también estimaciones cero o negativas. El panel indica el período histórico y las limitaciones de la proyección lineal. El flujo utiliza exclusivamente movimientos computables de todos los productos, con el mismo criterio que ingresos y gastos: contabilizados, de tipo NORMAL o REFUND y sin categoría NO_COMPUTABLE. Quedan fuera pendientes, duplicados, transferencias y liquidaciones. La fórmula es saldo actual de cuentas y monederos + (ingresos computables − gastos computables) / días del histórico × días del horizonte. Esta extrapolación no predice vencimientos ni fechas de cobro o pago. Un fallo de esta consulta se muestra en el panel y no impide cargar el resto de Inicio.

## Verificación

```bash
cd finance-dashboard
npm ci
npm run build
npm test
npx playwright install chromium
npm run test:movements
```

Las pruebas de navegador usan movimientos ficticios y respuestas HTTP simuladas. Cubren edición individual, cancelación, selección entre páginas, selección de más de 200 resultados, errores de guardado, filtros, tamaño de página, última página vacía, permisos de lectura, teclado y móvil. En Ingresos y gastos verifican además el alcance de la selección por categoría/período/tipo, respuestas tardías tras cambiar de vista, eliminación, actualización de totales y cierre de categorías vacías.

```bash
mvn -pl finance-server -am \
  -Dtest=ClassificationManagementServiceTest,JpaAdapterTest,ServerIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Las pruebas del servidor verifican el alcance del lote, la validación previa, la preservación de devoluciones, los permisos y la reversión de una actualización parcialmente ejecutada en JPA.
