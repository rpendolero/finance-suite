# Eliminar movimientos y detectar duplicados

En «Movimientos», un administrador puede pulsar «Eliminar» y confirmar el concepto, fecha e importe en el diálogo de la aplicación. El endpoint `DELETE /api/movements/{id}` devuelve 204. Solo elimina el movimiento elegido; conserva el producto, su saldo registrado y las reglas de clasificación. Los informes que consulten de nuevo los movimientos dejan de incluirlo. No requiere migración.

El borrado es definitivo. Si el banco o un fichero vuelven a enviar ese movimiento, se puede importar de nuevo. Para excluir un duplicado conservando su identidad y evitando que vuelva a computar, existe el tratamiento NO_COMPUTABLE / MOVIMIENTO_DUPLICADO.

La deduplicación actual usa `(product_id, external_id)`, respaldada por una restricción única. Si coincide, no inserta otra fila: reconcilia el estado pendiente/contabilizado y preserva la clasificación manual. Si el identificador se reutiliza con datos incompatibles, rechaza la importación.

Enable Banking usa el identificador del movimiento y, cuando falta, genera uno determinista con producto, fecha, importe, moneda y descripción normalizada. Identificadores diferentes entre API y ficheros, o productos diferentes para una misma cuenta, no se consideran duplicados automáticamente. Fecha e importe iguales por sí solos no permiten distinguir dos compras reales de un duplicado.
