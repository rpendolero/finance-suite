# Categorías de movimientos no computables

La categoría `NO_COMPUTABLE` contiene `LIQUIDACION_TARJETA`, `LIQUIDACION_PAYPAL`,
`TRASPASO_INTERNO` y `MOVIMIENTO_DUPLICADO`. El campo `kind` sigue determinando
la inclusión en métricas; los movimientos pendientes tampoco computan.

La edición manual sincroniza el tipo con la subcategoría. Al elegir una categoría
ordinaria el tipo vuelve a NORMAL, conservando REFUND si se solicita.

Al arrancar el servidor, Flyway aplica V6 automáticamente. Actualiza categoría y
subcategoría de movimientos y reglas con tipos excluidos, preservando su tipo y
metadatos. Las transferencias con tipo NORMAL o REFUND no se modifican. No hace
falta recategorizar todo ni ejecutar SQL manualmente. Los tipos incorrectos deben
revisarse aparte. Haz una copia de seguridad de la base de datos antes de actualizar.
