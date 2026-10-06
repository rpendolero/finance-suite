# Finance Suite 0.4.4 — identificación de varias tarjetas

Base: master, commit ae9e8ece323c922b6e8a626ee3a2dd89ec7b9392 (0.4.3).

El modelo ya permite varias tarjetas de un banco: cada tarjeta es un Product
CREDIT_CARD o DEBIT_CARD con id propio. Movement.productId identifica el producto;
la deduplicación usa (product_id, external_id), por lo que dos tarjetas pueden tener
movimientos con la misma referencia sin perder ninguno. No se necesita otra tabla Card.

## Cambios

- externalId opcional: referencia estable del producto facilitada por el banco.
  No confundir con Movement.externalId, que identifica cada movimiento.
- maskedPan opcional: exactamente `**** 1234`; solo se admiten los cuatro últimos
  dígitos. No es único y nunca se usa para deduplicar tarjetas.
- REST y bank_get_products exponen los nuevos campos; los filtros existentes de
  movimientos y resumen por productId ya permiten analizar una tarjeta concreta.
- La pareja (provider, externalId) es única si hay referencia, también en la base de datos.
- Un ID existente no puede cambiar de banco, tipo o referencia bancaria conocida.
  Los snapshots antiguos que omiten los nuevos campos conservan la identidad registrada.
- La cuenta vinculada debe existir, ser ACCOUNT y pertenecer al mismo banco.
- Flyway V4 añade columnas anulables sin cambiar IDs ni movimientos existentes.
  No modifica las migraciones anteriores. La base real utiliza Flyway, no Liquibase.

## Configuración

Usa un product-id distinto y estable por tarjeta (p.ej. kutxa-card-a y kutxa-card-b).
Registra primero la cuenta account-main. Hay snapshots de ejemplo en
finance-server/examples/card-a.json y card-b.json, y dos trabajos en
finance-importer/config/multiple-cards-example.yml. Sustituye referencias, saldos,
fechas, cuenta y archivos por los reales. Las referencias de ejemplo no son números PAN.

Para navegador usa también export-key distinto por tarjeta, con su propio card-name
en browser.providers.KUTXABANK.exports, siguiendo kutxabank-example.yml.
El exportKey selecciona la descarga; productId selecciona el destino de los movimientos.

No se asigna automáticamente identidad a partir del Excel. Un archivo seleccionado
para el trabajo incorrecto puede atribuirse a otra tarjeta: verifica esa configuración.
Si los movimientos históricos de varias tarjetas se importaron bajo un único ID,
no se pueden separar sin saber a qué tarjeta pertenece cada uno. No se reasignan.

## Validación

Pruebas JPA añadidas: dos tarjetas del mismo banco y cuenta, incluso con los mismos
cuatro últimos dígitos; referencias de movimientos iguales; reimportación idempotente;
conservación de metadatos; rechazo de referencia distinta y cuenta de otro banco.
Ejecutar con Java 21 y Maven: `mvn test`.

Resultado en Java 21.0.12: servidor 38 pruebas (37 correctas, 1 omitida);
importador 34 pruebas (30 correctas, 4 omitidas). Sin fallos ni errores.
La prueba MySQL/Testcontainers se omite por ausencia de Docker; otras cuatro
requieren archivos XLS reales opcionales. No se ha ejecutado acceso bancario real.
Se actualizaron dos tests previos del importador que estaban desfasados respecto
al constructor de ImporterService y a la lectura del valor de las fechas.
