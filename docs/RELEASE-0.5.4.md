# Finance Suite 0.5.4 — Normalización financiera

## Semántica contable

- **Ingresos**: abonos ordinarios contabilizados. No incluyen traspasos internos, retornos de inversión, desembolsos de préstamos ni devoluciones identificadas.
- **Gastos de consumo e intereses**: cargos normales, intereses financieros desglosados y devoluciones con signo contrario. Las devoluciones registradas en `INGRESOS / DEVOLUCIONES` aparecen como `DEVOLUCIONES_SIN_ASOCIAR` hasta asociarlas manualmente a una compra.
- **Ahorro e inversión** (`AHORRO_E_INVERSION`): aportaciones netas (cargos positivos en el resumen, rescates negativos); no es gasto de consumo.
- **Financiación** (`FINANCIACION`): cuotas netas pagadas. Se separan **capital** (`AMORTIZACION_CAPITAL`) e **intereses** (`INTERESES`) solamente cuando el movimiento ya llega desglosado. El resto se muestra como **sin desglose**.
- **Flujo disponible del período** = ingresos ordinarios − (consumo neto + intereses desglosados) − ahorro neto aportado − pagos netos de financiación + intereses ya incluidos en gastos (para no descontarlos dos veces). No es el saldo actual de ninguna cuenta y no incluye movimientos excluidos/pending.

Los movimientos de tipo no computable permanecen fuera de estas cifras. La retirada en cajero se mantiene computable **hasta revisión**, pues excluirla sin registrar los pagos efectivos provocaría infravalorar los gastos.

## Migraciones

La rama integra `V10__lodging_category_alignment.sql` y `V11__financial_category_normalization.sql`. **Aplicar con copia de seguridad previa de MySQL**. Flyway se ejecuta al iniciar el servidor.

V11 mueve automáticamente únicamente las asociaciones inequívocas:

| Origen | Destino |
| --- | --- |
| OCIO / REGALOS | COMPRAS / REGALOS |
| TRANSPORTE / FINANCIACION_VEHICULO | FINANCIACION / VEHICULO |
| VIVIENDA / HIPOTECA | FINANCIACION / HIPOTECA |
| SUSCRIPCIONES / TELEFONIA | VIVIENDA / TELEFONIA |
| IMPUESTOS / MULTAS | OTROS / MULTAS |
| HOGAR / SERVICIO_DOMESTICO | VIVIENDA / SERVICIO_DOMESTICO |

Conserva IDs, importe, fecha, tipo, origen manual y confianza del movimiento; actualiza también reglas futuras. Las subcategorías antiguas quedan inactivas (no se borran). La categoría HOGAR se desactiva únicamente si no contiene subcategorías activas ni movimientos o reglas remanentes. Crea además ALQUILER en Vivienda y subcategorías de inversiones y desglose de deuda.

### Pendientes para clasificación manual

Estas situaciones NO se reescriben masivamente:

```sql
SELECT category, subcategory, COUNT(*) AS movimientos
FROM movement
WHERE (category = 'VIVIENDA' AND subcategory = 'HIPOTECA_ALQUILER')
   OR (category = 'COMPRAS' AND subcategory = 'ONLINE')
   OR (category = 'EFECTIVO' AND subcategory = 'RETIRADA_CAJERO')
   OR (category = 'TRANSFERENCIAS' AND subcategory = 'TRANSFERENCIA_EXTERNA')
GROUP BY category, subcategory
ORDER BY movimientos DESC;
```

- Distinguir hipoteca de alquiler según contrato/concepto.
- Las compras online deben clasificarse por producto.
- Una retirada en cajero puede ser gasto o traspaso, según se registren compras con efectivo.
- Una transferencia externa puede ser una donación, un pago real o un traspaso entre cuentas.

Los movimientos sin desglose real de intereses y capital se mantienen bajo su subcategoría de préstamo; no se inventan cifras.

### Validación previa al despliegue

```sh
mvn -B verify
cd finance-dashboard && npm ci && npm test && npm run build
```

Verificar una vez arrancado el backend:
- `GET /api/dashboard/overview?from=2026-10-01&to=2026-10-31`
- `GET /api/dashboard/flows?from=2026-10-01&to=2026-10-31&direction=SAVINGS`
- `GET /api/dashboard/flows?from=2026-10-01&to=2026-10-31&direction=FINANCING`
- `GET /api/analysis/summary?from=2026-10-01&to=2026-10-31`

La aplicación móvil consume una API con nuevos campos opcionales; cualquier consumidor que genere clases rígidas a partir del esquema deberá actualizar sus DTO si quiere mostrar esos nuevos importes.
