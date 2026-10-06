# Finance Importer 0.4.3

Aplicación local de consola. No abre puertos, no conecta a MySQL y no contiene lógica MCP.

Ejecuta un trabajo por invocación: archivo CSV normalizado o descarga Playwright con sesión local. Envía el CSV y un snapshot opcional a `finance-server` por HTTPS con el usuario `importer`. Conserva descargas cuando falla y elimina solo las descargas propias confirmadas.

Guía completa: [../README.md](../README.md). Selecciona el trabajo con `--finance.importer.job=nombre`. Los trabajos y selectores solo se configuran localmente. Credenciales y perfiles no se envían al servidor.

## ING: cuenta NÓMINA (actualización 0.2.2)

Se incorpora lectura del XLS binario de ING, hoja `Movimientos`, con las siete
columnas del archivo real aportado. Se valida la estructura antes de enviar,
se transforman fechas e importes y se mantienen las categorías de ING. No se
infieren transferencias internas, devoluciones ni liquidaciones solo por su texto.
Subcategoría/comentario y saldo por movimiento no se envían porque el contrato
actual del servidor no tiene esos campos. El saldo de cuenta sigue requiriendo
un snapshot independiente. El archivo original proporcionado manualmente nunca
se borra; las conversiones y descargas se borran únicamente tras envío correcto.

Desde la raíz: `mvn clean install`. Después, desde `finance-importer`, copia tu
XLS a `../.private` y configura `FINANCE_SERVER_URL` y
`FINANCE_IMPORTER_PASSWORD`. Debe existir el producto `ing-main` en el servidor.

```bash
java -jar target/finance-importer-0.4.3.jar \
  --spring.config.additional-location=file:./config/ing.yml \
  --finance.importer.job=ing-excel
```

Para descargar con Chrome y perfil bancario local separado:

```bash
java -jar target/finance-importer-0.4.3.jar \
  --spring.config.additional-location=file:./config/ing.yml \
  --finance.importer.job=ing-browser
```

Completa login y 2FA en la ventana; se espera hasta tres minutos a que aparezca
el enlace de la cuenta. Ajusta `account-name` si hay varias cuentas: una
coincidencia ambigua falla en lugar de escoger la primera.

El recorrido usa los enlaces y botones grabados por el usuario. Exporta el
histórico disponible SIN aplicar un periodo: el archivo aportado incluía
julio-octubre pese al filtro septiembre-octubre grabado. Se han omitido los clics
sobre SVG y el ID dinámico de fecha. Esta variante debe validarse en tu equipo;
puede requerir completar la búsqueda antes de que aparezca Descargar movimientos.
No se ha probado una sesión bancaria real aquí. Cuentas distintas de NÓMINA no están cubiertas por esa grabación.
Las tarjetas de crédito tienen su propio recorrido, descrito al final.

La identidad se calcula con SHA-256 de fecha valor, importe, saldo y descripción,
más número de aparición para filas totalmente iguales. No depende de categoría,
comentario ni posición global. Reimportar el mismo archivo es estable y conserva
compras repetidas. Un banco que corrija texto/saldo o exportaciones que incluyan
solo parte de filas exactamente iguales pueden requerir reconciliación manual.
No es un identificador oficial del banco.

## ING: tarjeta de crédito (0.2.2)

Se añade el formato XLS `Tarjetas`, título `Tarjeta Crédito`, con cabeceras en la
fila 5. Importe en columna H, estado en G y descripción en D. Solo se acepta
el estado observado `Liquidado` y se convierte a `BOOKED`. Es una compra
contabilizada: NO se convierte a `CARD_SETTLEMENT`. Un estado distinto detiene
el lote para evitar interpretaciones no verificadas. No incluye tarjeta de débito.

Para importar el fichero, coloca una copia en `../.private`:

```bash
java -jar target/finance-importer-0.4.3.jar \
  --spring.config.additional-location=file:./config/ing.yml \
  --finance.importer.job=ing-credit-excel
```

Para descargarlo mediante el recorrido grabado:

```bash
java -jar target/finance-importer-0.4.3.jar \
  --spring.config.additional-location=file:./config/ing.yml \
  --finance.importer.job=ing-credit-browser
```

Ajusta `card-name`, `from` y `to` en `ing.yml`. El recorrido selecciona
la tarjeta tras login, abre Más opciones de búsqueda, rellena Desde/Hasta,
busca y guarda Descargar Excel. Debe existir el producto `ing-credit` de tipo
`CREDIT_CARD`, proveedor `ING`, vinculado a `ing-main`. No uses el mismo ID de
producto para cuenta y tarjeta. Registra la deuda y el límite con los datos
reales mediante el REST existente: la exportación no contiene esos saldos.

Los pagos de tarjeta reflejados en la cuenta deben reconciliarse y clasificarse
como `CARD_SETTLEMENT` en el servidor para no sumarlos otra vez a las compras.
Este XLS no permite identificar el cargo de liquidación de la cuenta y no lo
clasifica automáticamente. La identidad de tarjeta usa fecha valor, importe,
descripción con espacios normalizados y número de aparición. No incorpora
categoría ni estado. Para filas completamente iguales exportadas parcialmente,
o correcciones de fecha/importe/descripción, se requiere revisión de duplicados.

El código grabado incluía `page1` sin declararlo: la integración ya usa la página
del perfil persistente, apertura de ING, espera de login y guardado del fichero.
La navegación en vivo continúa pendiente de validación por el usuario.
