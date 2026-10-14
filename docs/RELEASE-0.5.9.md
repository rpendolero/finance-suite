# Finance Suite 0.5.9

## Cambios

- Productos: botón «Ver movimientos» con resumen del producto seleccionado en el período activo.
- Calendario: pulsación sobre un día para consultar sus movimientos.
- Ventana modal propia de Finance Suite, responsive y sin Bootstrap ni diálogos nativos.
- Resumen de operaciones computables, ingresos, gastos, balance y detalle con categoría.
- Exclusión de movimientos con categoría `NO_COMPUTABLE` y tipos `INTERNAL_TRANSFER`, `CARD_SETTLEMENT`, `WALLET_SETTLEMENT`, `NON_COMPUTABLE` y `DUPLICATE`.
- Consulta paginada mediante el endpoint REST existente `/api/movements/search`.

No se requieren migraciones de base de datos ni cambios de configuración.

## Validación

Pendiente de ejecución de compilación y pruebas funcionales en el entorno de despliegue.
