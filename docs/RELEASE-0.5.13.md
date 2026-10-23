# Finance Suite 0.5.13

## Cambios

- Previsión de saldo híbrida en `LiquidityForecastService`: detecta patrones mensuales de ingresos y gastos computables en EUR.
- Exige al menos tres movimientos separados entre 25 y 35 días para considerar un patrón mensual.
- Proyecta los patrones hasta el horizonte solicitado (1 a 90 días).
- Excluye los movimientos recurrentes identificados de la extrapolación del flujo variable para evitar doble cómputo.
- Actualiza el panel `ForecastCard` y las versiones Maven y npm a **0.5.13**.

## Limitaciones conocidas

- El período seleccionado puede no contener suficiente histórico para detectar nóminas: con solo octubre se detectan cero patrones mensuales.
- La previsión no confirma fechas futuras de cobro ni permite registrar vencimientos manuales.
- La cifra mezcla saldos de productos líquidos con flujos económicos de todos los productos: **no debe interpretarse como saldo bancario garantizado**.
- El panel inicial no desglosaba ingresos y gastos; la corrección posterior se desarrolla en `fix/forecast-income-expense-breakdown`, todavía fuera de esta release.

## Trazabilidad

PR [#34](https://github.com/rpendolero/finance-suite/pull/34), merge `f0a4d025564d454a474257eac2dc169727327b98`.

## Validación

La integración GitHub se completó, pero no hay constancia de compilación, tests o publicación de imágenes Docker en este proceso.
