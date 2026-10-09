# Finance Dashboard 0.5.3

Dashboard React/TypeScript de Finance Suite. Consume exclusivamente la API REST del finance-server.

## Desarrollo

```bash
cd finance-dashboard
npm install
npm run dev
```

Vite publica en http://localhost:5173 y redirige /api a http://localhost:8081.

Si la API no está disponible, el dashboard muestra el error de conexión.

## Seleccionar período

El selector superior conserva «Este mes», «Mes anterior», «Últimos 3 meses», «Últimos 6 meses», «Este año» y «Personalizado». También permite elegir de enero a diciembre. Al seleccionar un mes aparece el selector «Año», inicialmente con el año actual; el intervalo incluye el mes completo y respeta los años bisiestos. El año elegido para los meses se conserva al cambiar de opción, sin alterar los períodos relativos. Las fechas utilizadas se muestran junto al selector.

## Calendario

El calendario muestra las columnas Lunes, Martes, Miércoles, Jueves, Viernes, Sábado y Domingo. Cada fecha ocupa la columna de su día de la semana, con huecos antes del primer día del período. Conserva el día y el mes, el número de operaciones y los importes de ingresos y gastos. Los períodos que abarcan varios meses mantienen la secuencia de semanas. En pantallas pequeñas permite desplazamiento horizontal para conservar las siete columnas.

## Ingresos y gastos

El apartado «Ingresos y gastos» permite alternar entre ambas vistas y utiliza el período elegido en la cabecera. Cada vista muestra el total y las categorías. Al pulsar una categoría se despliegan sus movimientos, paginados de 25 en 25; una segunda pulsación oculta el detalle. Solo se abre una categoría a la vez. Al cambiar de período o de vista se cierra el detalle.

La dirección depende del signo del importe, no del nombre de la categoría. Los ingresos son positivos y los gastos negativos. Las devoluciones marcadas REFUND conservan su importe positivo y reducen el gasto. Los movimientos pendientes y no computables quedan excluidos. Los totales y desgloses siempre corresponden al período completo, aunque solo se muestre una página de movimientos.

La API debe incluir `GET /api/dashboard/flows?from=YYYY-MM-DD&to=YYYY-MM-DD&direction=INCOME|EXPENSE&offset=0&limit=25`. El parámetro opcional `category` filtra los movimientos y sus totales antes de paginarlos. Actualiza API y frontend juntos; no necesita cambios de base de datos.

## Clasificar movimientos

«Revisar», «Movimientos» y el detalle de cada categoría comparten el mismo editor de tratamiento, categoría y subcategoría. Con rol ADMIN, «Solo este» modifica el movimiento elegido. «Aplicar al comercio» modifica el histórico del mismo comercio normalizado, incluidos los movimientos clasificados manualmente y de otros períodos o productos, y guarda una regla para futuras importaciones. Otros comercios no se recategorizan con esta acción. Las devoluciones existentes conservan su tratamiento al aplicar una categoría normal al comercio.

Después de guardar se actualizan los movimientos, las categorías y el resumen del dashboard. Si un movimiento pasa a otra categoría o deja de ser computable, desaparece del desplegable anterior. «Movimientos» mantiene la opción de eliminar. El rol READER puede consultar los datos sin acciones de escritura.

## Build

```bash
npm run build
```

## Comprobaciones del editor

```bash
npm test
```

Comprueba el selector de períodos, los meses completos y bisiestos, el renderizado del editor para ADMIN y READER, los tratamientos válidos, el alcance de las peticiones de clasificación, la importación y la cabecera CSRF.
