# Operaciones

Fechas `YYYY-MM-DD`, meses `YYYY-MM`. Todos los cálculos en EUR y `BigDecimal`; importes positivos = abonos y negativos = cargos. Movimientos `PENDING` excluidos del análisis de gasto. Consulta `quality` antes de usar resultados.

| Herramienta MCP (lectura) | REST GET equivalente | Resultado |
|---|---|---|
| bank_get_provider_summary | /api/analysis/providers/{provider} | Resumen por KUTXABANK, ING o PAYPAL |
| bank_get_products | /api/products | Cuentas, débito/crédito, PayPal, proveedor, saldo y fecha |
| bank_get_transactions | /api/movements | Movimientos con offset/limit y producto opcional |
| bank_get_summary | /api/analysis/summary | Ingresos, gasto neto por categoría, ahorro |
| bank_get_monthly_summary | /api/analysis/summary | Igual, con fechas del mes |
| bank_compare_periods | /api/analysis/compare | Diferencia de ingresos, gastos y ahorro |
| bank_get_recurring_expenses | /api/analysis/recurring | Candidatos a recibos mensuales |
| bank_get_anomalies | /api/analysis/anomalies | Importes atípicos del comercio |
| bank_get_card_exposure | /api/analysis/cards | Deuda, límite y utilización de crédito |
| bank_forecast_cash_flow | /api/analysis/forecast | Estimación lineal, hasta 90 días |
| bank_get_data_quality | /api/analysis/quality | Cobertura observada, pendientes y sin clasificar |
| bank_get_monthly_trend | /api/analysis/trend | Serie de hasta 36 meses |
| bank_get_merchant_spending | /api/analysis/merchants | Gasto neto por comercio |
| bank_get_budget_status | /api/analysis/budgets | Presupuesto, consumido y restante |
| bank_get_reconciliation_candidates | /api/analysis/reconciliation | Pares a revisar, sin modificar |
| bank_get_recurring_increases | /api/analysis/recurring-increases | Variación del último recibo |
| bank_get_account_cash_flow | /api/analysis/cash-flow | Flujo efectivo de cuentas y monederos |
| bank_get_financial_report | Composición de las consultas anteriores | Informe conjunto y posición neta declarada |

Parámetros habituales REST: `from`, `to`, `productId` opcional. En compare: `previousFrom`, `previousTo`. En trend: `fromMonth`, `toMonth`. Budgets: `month`. Forecast: `days`. Movements: `offset=0`, `limit=100` (máximo 500). MCP: productId puede ser cadena vacía para todos.

## Administración REST (rol admin)

| Método y endpoint | Acción |
|---|---|
| PUT /api/products/{id} | Registrar/actualizar cuenta, tarjeta o WALLET PayPal, proveedor y saldo fechado |
| DELETE /api/products/{id} | Eliminar producto y movimientos; acción destructiva local |
| POST /api/importer/products/{id}/batches | Multipart file y snapshot product opcional, solo importer/admin |
| POST /api/products/{id}/imports | Multipart `file`: importar CSV normalizado |
| PATCH /api/movements/{id}/classification | Corregir categoría y tipo de movimiento |
| GET /api/rules | Consultar reglas |
| PUT /api/rules/{id} | Guardar regla por texto contenido y prioridad |
| DELETE /api/rules/{id} | Eliminar regla |
| PUT /api/budgets | Guardar presupuesto por mes/categoría |

No existen operaciones para pagar, transferir o comprar productos bancarios. Los permisos de escritura se refieren exclusivamente a tu copia local.

Ejemplo de regla (prioridad menor primero; se aplica a nuevos movimientos UNCLASSIFIED, nunca a clasificaciones explícitas):

```json
{"id":"supermercado","priority":10,"contains":"MERCADONA","category":"GROCERIES","kind":"NORMAL"}
```

Ejemplo de presupuesto:

```json
{"month":"2026-10","category":"RESTAURANTS","amount":180.00}
```

Ejemplo de corrección de liquidación:

```json
{"category":"CARDS","kind":"CARD_SETTLEMENT"}
```

## Tratamiento contable

- `NORMAL`: compras/cargos o ingresos, según el signo.
- `REFUND`: devolución positiva; reduce gasto de la categoría, no aumenta ingresos.
- `INTERNAL_TRANSFER`: excluir del gasto/ingreso económico; marcar ambos lados tras confirmar.
- `CARD_SETTLEMENT`: pago de tarjeta; excluir ambos lados del análisis económico. Las compras individuales sí cuentan.
- `DUPLICATE`: copia de compra ya presente en otro producto, excluirla tras revisión.
- `BOOKED`: contabilizado. `PENDING`: autorización pendiente, solo visible en movimientos/calidad.

El flujo de cuentas y monederos incluye todos los movimientos BOOKED de las cuentas/monederos: una liquidación sí reduce dinero de la cuenta. Por eso gasto económico y flujo efectivo son consultas separadas. Para tarjetas de crédito, saldo negativo representa deuda. No sumes saldo de tarjetas de débito al patrimonio.

El gasto completo requiere que estén importadas las compras de tarjeta. Excluir una liquidación sin esas compras infravalora el gasto. Los candidatos por importe/fecha no se excluyen automáticamente. El sistema no conoce de forma fiable la cobertura completa del banco ni la condición de todos los movimientos.

## PayPal y varias entidades

Consulta consolidada incluye las tres entidades; consulta por proveedor filtra sus productos. Las operaciones deben clasificarse para que transferencias propias, recargas PayPal y compras reflejadas en más de una fuente no distorsionen ingresos/gastos. La identidad estable es por producto; los perfiles del navegador se aíslan por entidad en el importador local. Registrar el banco financiador de PayPal en `linkedAccountId` permite candidatos de duplicados entre ambos. Para PayPal financiado por una tarjeta, vincula ese producto: la conciliación admite cuenta o tarjeta como producto vinculado.

PayPal: bruto y comisión pueden convertirse en dos movimientos. No importar simultáneamente bruto y neto como ingresos. Este proyecto contiene ejemplos normalizados, no un parser nativo validado para todos los idiomas/formatos del proveedor.

## Identidad y conciliación

Idempotencia por `(productId, external_id)` con clave única en MySQL. CSV repetido no añade filas. Un external_id con importe/fecha/concepto diferentes se rechaza para revisión; no se sobrescribe silenciosamente. Un movimiento con el mismo identificador pasa de PENDING a BOOKED si conserva esos datos. Una corrección de fecha/importe o identificador distinto requiere conciliación manual. No genera identificadores desde importe/fecha porque borraría compras legítimas iguales.

Las importaciones en MySQL son transaccionales. El endpoint batches guarda snapshot y movimientos en la misma transacción; el endpoint antiguo de CSV administrativo requiere producto existente. Importaciones simultáneas con el mismo external_id pueden fallar por clave única: repetir después de terminar la primera; no hay duplicado persistido. `inserted` cuenta altas; las promociones de pendientes no se cuentan como altas y `duplicates` incluye estas coincidencias.

Recurrencia: tres cargos del mismo comercio con separaciones de 25..35 días. No detecta recurrencias semanales/anuales ni agrupa textos similares automáticamente. Anomalías: importe superior al doble de la mediana de cargos anteriores del mismo comercio y aumento absoluto >25 EUR; requiere tres antecedentes. Forecast: media de flujo de cuentas del periodo; no calcula intereses, vencimientos, nóminas futuras ni disponibilidad de crédito.
