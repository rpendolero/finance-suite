# Finance Dashboard 0.5.1

Dashboard React/TypeScript de Finance Suite. Consume exclusivamente la API REST del finance-server.

## Desarrollo

```bash
cd finance-dashboard
npm install
npm run dev
```

Vite publica en http://localhost:5173 y redirige /api a http://localhost:8081.

Si la API no está disponible, el dashboard muestra el error de conexión.

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

Comprueba el renderizado del editor para ADMIN y READER, los tratamientos válidos, el alcance de las peticiones de clasificación y la cabecera CSRF.
