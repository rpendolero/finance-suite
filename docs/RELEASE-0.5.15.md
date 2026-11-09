# Finance Suite 0.5.15

Fecha: 9 de noviembre de 2026.

## Cambios

- Incorpora la PR [#40](https://github.com/rpendolero/finance-suite/pull/40), rama `fix/kutxabank-card-extracts`.
- Reconoce «Consulta de extractos» de tarjeta Kutxabank dentro de `KUTXABANK_CARD_XLS`, en la API y el importador automático.
- Omite filas de resumen de saldo anterior, total dispuesto y total disponible solo cuando no contienen fechas ni tarjeta. Las filas inválidas rechazan el fichero completo.
- Conserva la fecha original de operación, los conceptos, las cuotas y los importes con su signo.
- Identifica `PAGO RECIBO` como liquidación de tarjeta no computable, sujeta a las reglas de clasificación existentes.
- Genera identificadores estables con fecha de proceso, tarjeta mediante hash y ordinal para operaciones idénticas repetidas. Reimportar el mismo extracto en el mismo producto conserva las clasificaciones manuales.
- Actualiza la ayuda de importación y las guías de formato y validación.
- Unifica en `0.5.15` los cinco POM Maven, los paquetes y locks npm del dashboard y del puente MCP, y las referencias de arranque y compilación.

## Compatibilidad y despliegue

- Sin nuevas migraciones de base de datos ni nuevos endpoints.
- El formato nativo sigue siendo Excel `.xls`; no se añade soporte `.xlsx`.
- La deduplicación usa `(productId, externalId)`. Importación manual y Enable Banking pueden aportar identificadores distintos para la misma operación.
- Reconstruye las imágenes del servidor y del dashboard para desplegar los cambios:

```bash
./scripts/publish-docker.sh 0.5.15 rpendolero
```

La integración en GitHub no publica imágenes Docker ni actualiza los contenedores desplegados.

## Validación

- La implementación de la PR #40 superó Finance Suite CI: backend Maven y dashboard, incluida la prueba de interacción de movimientos (ejecución [42302417457](https://github.com/rpendolero/finance-suite/actions/runs/42302417457)).
- La PR documenta validación local con un extracto privado: 75 movimientos importados y 0 altas al repetir la carga. El fichero bancario privado no forma parte del repositorio.
- El cambio de versión actualiza metadatos y documentación sin modificar dependencias.
