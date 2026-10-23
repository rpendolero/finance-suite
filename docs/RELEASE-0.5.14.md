# Finance Suite 0.5.14

## Cambios

- Corrige el literal `\\n` visible en el panel de previsión.
- Desglosa ingresos estimados, gastos estimados y flujo neto en el dashboard.
- Amplía la respuesta de previsión con `estimatedIncome`, `estimatedExpenses`, `recurringIncome`, `recurringExpenses` y `recurringPatterns`.
- Mantiene compatibilidad con el constructor previo del registro `Forecast` en Java.
- Actualiza los módulos Maven y dashboard npm a 0.5.14.

## Limitaciones

- Los ingresos previstos son estimaciones estadísticas si no se reconocen patrones mensuales.
- La detección de recurrencias requiere al menos tres operaciones mensuales dentro del histórico seleccionado.
- No se han implementado todavía vencimientos manuales ni un calendario diario de tesorería.

## Validación

Cambios revisados a nivel de código; no se han ejecutado aquí las pruebas Maven, la compilación Vite ni una validación de datos reales. No equivale a publicación Docker.
