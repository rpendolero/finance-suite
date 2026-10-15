# Finance Suite 0.5.11

## Cambios

- Las filas de Movimientos y de las categorías de Ingresos y gastos abren un modal para editar categoría, subcategoría y tratamiento, con Guardar/Cancelar.
- Recategorización masiva con selección de página o de todos los resultados del filtro; nuevo `PATCH /api/movements/classification` para ADMIN con validación y persistencia transaccional.
- Tamaño de página persistente de 25/50/100 elementos compartido entre ambas pantallas.
- Criterio común para ingresos, gastos, calendario, evolución, productos y detalle: solo movimientos contabilizados computables. Devoluciones positivas como ingresos; financiación y ahorro/inversión negativos como gastos, sin descontarlos de nuevo del flujo disponible.
- Se mantiene la evolución en Inicio y se retira la pantalla independiente.
- Previsión adaptada al contrato real de la API, mostrando también cero, negativos y errores de carga localizados en el panel.
- La previsión usa el neto computable de todos los productos, incluidas tarjetas. Excluye pendientes, duplicados, transferencias, liquidaciones y categoría NO_COMPUTABLE.
- Fórmula: saldo registrado de cuentas y monederos + (neto computable / días del histórico) × días del horizonte. Por ejemplo, 227,14 € de saldo, 640,20 € de ingresos y 4.487,37 € de gastos en 15 días producen −7.467,20 € a 30 días. No predice vencimientos ni fechas de cobro o pago.
- Versiones de Maven, importer, dashboard y puente MCP actualizadas a 0.5.11.
- Se corrige el test del sincronizador para simular explícitamente la clase final con el mock maker inline, conservando el configurado para el resto de las pruebas.

## Actualización

Actualizar servidor y dashboard juntos. No se añaden migraciones de base de datos ni se reclasifican movimientos existentes.

## Verificación

CI: compilación Maven y pruebas del servidor (incluidas JPA/MySQL y coherencia financiera), compilación del dashboard, pruebas de componentes y pruebas de interacción en Chromium.

[Comportamiento y criterios de cálculo](MOVEMENT-EDITING.md).
