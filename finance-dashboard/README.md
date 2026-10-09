# Finance Dashboard 0.5.0

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

El apartado «Ingresos y gastos» permite alternar entre ambas vistas y utiliza el período elegido en la cabecera. Cada vista muestra el total, las categorías, los comercios u orígenes del ingreso y los movimientos paginados de 25 en 25.

La dirección depende del signo del importe, no del nombre de la categoría. Los ingresos son positivos y los gastos negativos. Las devoluciones marcadas REFUND conservan su importe positivo y reducen el gasto. Los movimientos pendientes y no computables quedan excluidos. Los totales y desgloses siempre corresponden al período completo, aunque solo se muestre una página de movimientos.

La API debe incluir `GET /api/dashboard/flows?from=YYYY-MM-DD&to=YYYY-MM-DD&direction=INCOME|EXPENSE&offset=0&limit=25`. Actualiza API y frontend juntos; no necesita cambios de base de datos.

## Build

```bash
npm run build
```
