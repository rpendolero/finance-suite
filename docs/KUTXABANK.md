# Cuenta Kutxabank

El recorrido `KUTXABANK_ACCOUNT` reproduce la grabación: Cuentas → Consultas → seleccionar cuenta → Movimientos → Entre fechas → MOSTRAR → DESCARGAR XLS. El acceso y el segundo factor se completan manualmente en Chrome. No guarda DNI ni contraseña bancaria en la configuración.

Desde `finance-importer`, crea una copia privada de `config/kutxabank-example.yml`, ajusta `from`, `to` y `product-id`, y configura `KUTXABANK_ACCOUNT_NAME` con el nombre accesible del botón de cuenta registrado por codegen. La coincidencia del nombre permite el prefijo grabado; si coincide con más de una cuenta Playwright falla, sin seleccionar una al azar. El producto debe existir en el servidor; puede proporcionarse un snapshot con `product-file`, siguiendo los ejemplos existentes. Usa un alias de producto, no el número de cuenta.

Para importar el XLS ya descargado:

```bash
export FINANCE_IMPORTER_PASSWORD='credencial-del-importador-de-al-menos-20-caracteres'
java -jar target/finance-importer-0.5.2.jar \
  --spring.config.additional-location=file:./config/kutxabank.yml \
  --finance.importer.job=kutxabank-excel
```

Para descargarlo desde la web, configura el nombre de cuenta en tu entorno privado y selecciona `--finance.importer.job=kutxabank-browser`. El importador abre Chrome, espera el enlace Cuentas tras el acceso, descarga el archivo y lo convierte antes del envío. Los perfiles y las descargas usan almacenamiento privado.

Formato observado: hoja `Listado`, cabecera `fecha`, `concepto`, `fecha valor`, `importe`, `saldo`. La muestra tiene 84 movimientos. La conversión usa la fecha de operación (`fecha`), conserva el signo del importe y el concepto, asigna EUR, categoría UNCLASSIFIED y estado BOOKED. No toma el saldo de una fila como saldo actual del producto. La fecha valor no se transporta porque el CSV canónico solo tiene una fecha.

El identificador se deriva de fecha, importe, saldo y concepto; un contador conserva filas repetidas idénticas. Es estable para reimportaciones del mismo extracto. El archivo no proporciona un identificador bancario único: cambios en esos datos o exportaciones parciales con repeticiones idénticas pueden alterar la deduplicación.

Validado el XLS adjunto y escenarios sintéticos. La navegación reproduce la grabación proporcionada, pero no se ha probado contra una sesión bancaria autenticada real. Si Kutxabank abre otra pestaña, habrá que seleccionar esa página antes de ejecutar el recorrido. El `page1` no declarado de la grabación se sustituye por la página recibida por el adaptador.

Prueba opcional con una muestra privada:

```bash
mvn -pl finance-importer -am test -Dkutxabank.sample=/ruta/privada/movimientos.xls
```

Las trazas en inglés siguen la navegación, búsqueda, descarga y conversión sin escribir la cuenta ni el contenido del extracto.

## Tarjetas

`KUTXABANK_CARD` reproduce Tarjetas → Consultas → Movimientos → Entre fechas → MOSTRAR → DESCARGAR XLS. La descarga se ejecuta una sola vez. Selecciona `kutxabank-card-excel` para un archivo local o `kutxabank-card-browser` para el navegador. Ajusta el `product-id` al producto de tarjeta registrado (crédito o débito, según corresponda).

La grabación no contiene una selección de tarjeta: el recorrido exporta la tarjeta activa por defecto de esa pantalla. Si tienes varias, habrá que añadir la selección concreta antes de automatizar esa ejecución.

El XLS de tarjeta también usa la hoja Listado, pero sus cabeceras son `fecha`, `concepto`, `fecha valor`, `importe de la operación`. Se distingue de la cuenta por las cabeceras completas, no solo por la hoja. La muestra aportada contiene 20 movimientos; se conserva el signo de cada importe y no se inventa un saldo. Los identificadores llevan prefijo `kutxabank-card-`.

El formato no aporta estado ni tipo de operación: la conversión asigna BOOKED y NORMAL por defecto. Un importe positivo no se clasifica automáticamente como devolución. Tampoco demuestra que sea una tarjeta de crédito o débito; eso lo determina el producto. La deduplicación sin saldo usa fecha, importe, concepto y ocurrencia, con las limitaciones descritas anteriormente.

Para verificar una muestra privada añade `-Dkutxabank.card.sample=/ruta/privada/tarjeta.xls`. No se incluyen los extractos en el ZIP. La navegación está pendiente de validación en sesión bancaria real.
