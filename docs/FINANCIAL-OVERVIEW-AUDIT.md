# Auditoría e implementación del resumen financiero

Fecha: 9 de noviembre de 2026. Rama local: `improve/financial-overview`.
Base comprobada: `origin/master`, commit `18a3e89d8d892d54ba2af7b2a57c8d93b30d6317`, versión 0.5.15.
La consulta a GitHub confirmó la PR #40 fusionada y ninguna PR abierta. No se duplica la importación Kutxabank.

**Estado: implementación terminada; validación visual e interacción E2E incompletas.** No se ha publicado, fusionado, desplegado ni cambiado la versión. No se han utilizado cuentas bancarias ni datos personales.

## Alcance seleccionado

| Orden | Mejora | Impacto | Riesgo | Esfuerzo |
|---|---|---|---|---|
| 1 | Rechazo explícito de agregados de movimientos computables no EUR | Evita sumar unidades distintas y omitirlas silenciosamente de la previsión | Medio: una consulta inválida devuelve 400 hasta corregir sus datos | Bajo |
| 2 | Previsión con días realmente observados | Evita dilución al seleccionar un mes completo aún abierto o un rango personalizado futuro | Bajo: cambia el resultado de esas selecciones | Bajo |
| 3 | Inicio con saldo, resultado y estimación diferenciados, enlaces y estados de carga | Trazabilidad, lectura diaria y recuperación de errores | Medio: composición y navegación nuevas; falta E2E | Medio |

No se afirma que existan monedas mezcladas en los datos del usuario. El defecto monetario se reproduce en los servicios con datos sintéticos que el record `Movement` admite. `V1__ledger.sql` declara un CHECK EUR; MySQL que lo aplique puede impedir esos registros antes de consultar. El dominio tiene comentada la validación EUR y Enable Banking conserva la moneda recibida: la defensa de consulta cubre esa inconsistencia sin fingir una conversión. No se incorpora soporte multimoneda general.

## Evidencia antes/después

| Hallazgo | Ubicación y reproducción | Antes | Después |
|---|---|---|---|
| Alto: agregados sin unidad homogénea | `DashboardAnalysisService.included`, `FinancialSummaryService.summarize`: ingreso EUR y gasto USD computables; prueba `mixedCurrenciesCannotBeReportedAsEurosOrSilentlyOmittedFromForecast` | El resumen mezcla los importes; la previsión filtra USD sin avisar | Los agregados modificados rechazan la consulta con error explicable; no se cambia ni borra el movimiento |
| Alto: divisor futuro | `LiquidityForecastService.forecast`: reloj fijo 2026-11-09, histórico 1–30 noviembre, ingreso 2.000 y gasto 100, horizonte 30 días | Ingreso previsto 2.000: divisor 30 pese a solo 9 días observados | Ingreso 6.666,67; gasto 333,33; neto 6.333,34; saldo de partida 1.900 → estimado 8.233,34 |
| Medio: histórico enteramente futuro | Mismo servicio; rango 10–19 noviembre con reloj 9 noviembre | Proyección de flujo cero que parece disponible | Error de histórico no transcurrido; la interfaz lo explica |
| Alto: cifras anteriores tras fallo de período | `App.tsx`, efecto de carga: cargar un período y hacer fallar el siguiente | `overview` anterior permanece; al terminar la carga puede etiquetarse con las nuevas fechas | Limpieza de resumen y control de contexto; error y reintento explícitos. Verificación de interacción pendiente |
| Medio: interpretación del panel | `App.tsx` anterior y `FinancialOverview.tsx` nuevo | Seis KPI con igual peso; «Saldo total» descrito como disponible; leyenda de categorías cortada en siete entradas | Saldo registrado con fechas de cuentas; resultado destacado; ingresos/gastos abren sus detalles con el mismo período; todas las categorías y tabla de evolución; estimación separada |

Las tres primeras regresiones se ejecutaron también contra la base sin modificar: 6 casos, **3 fallos esperados** (moneda, divisor, histórico futuro). Los mismos seis casos pasan con la implementación. La nueva comprobación HTTP verifica 400 y `ProblemDetail` en resumen, flujos y previsión con moneda inválida. Esto es prueba de aplicación con ledger simulado; no una conexión bancaria ni MySQL real.

El preset **Este mes ya terminaba en hoy** antes del cambio. La corrección del divisor afecta especialmente al selector de mes por nombre y rangos personalizados que incluyen días futuros. La proyección sigue siendo una heurística: una nómina con menos de tres ocurrencias puede extrapolarse como flujo variable. No debe interpretarse como calendario de nóminas o liquidaciones reales.

## Reglas financieras y ejemplo obligatorio

Desde cero: nómina +2.000; traspaso propio −300/+300; compra en tarjeta −100; liquidación −100 en cuenta/+100 en tarjeta.

| Magnitud | Resultado | Qué representa |
|---|---:|---|
| Ingresos computables | 2.000 € | Solo nómina |
| Gastos computables | 100 € | Compra una vez |
| Resultado del período | 1.900 € | Ingresos menos gastos |
| Cuenta diaria tras liquidación | 1.600 € | Nómina − traspaso − liquidación |
| Cuenta reserva | 300 € | Traspaso recibido |
| Saldo líquido agregado | 1.900 € | Dos cuentas, según snapshots suministrados |
| Deuda de tarjeta tras liquidación | 0 € | Compra satisfecha mediante liquidación |

Antes de liquidar habría 2.000 € en cuentas y 100 € de deuda de tarjeta (saldo de tarjeta −100 según el dominio), mientras el resultado ya sería 1.900 €. El saldo de Inicio suma cuentas/monederos y **no descuenta deuda de tarjetas**. No es patrimonio neto ni una reconstrucción de saldo al cierre del período.

La prueba comprueba agregados usando los snapshots anteriores; no ejecuta transferencias ni recalcula snapshots desde movimientos. «No computable» sigue excluido de ingresos/gastos y conserva los registros. Las liquidaciones siguen afectando al efectivo de la cuenta cuando se registran los saldos del banco. Una operación mal clasificada no se corrige automáticamente por coincidir importe/fecha.

Devolución positiva de 20 €: ingresos 2.020, gastos 100, resultado 1.920. Se conserva el criterio existente: devolución como ingreso; no se reduce silenciosamente el gasto de su categoría. Principal/intereses solo se desglosan con las subcategorías existentes; no se infieren desde una transferencia de tarjeta a cuenta. Ahorro y financiación siguen formando parte del resultado; su desglose no se resta otra vez.

La previsión usa solo días transcurridos, excluye futuros realizados y separa histórico de ventana futura. El caso de nóminas 2-sep/2-oct/2-nov, calculado el 9-nov a 30 días, proyecta solo la nómina del 2-dic; no vuelve a añadir la ya realizada del 2-nov. Se mantiene `BigDecimal`, división explícita a dos decimales y `HALF_UP`.

## Archivos y compatibilidad

- `finance-server/.../application/service/FinancialMovementAllocation.java`: comprobación común de EUR; aplicada en dashboard, resumen y previsión.
- `LiquidityForecastService.java`: límite temporal efectivo, rechazo de histórico futuro, hipótesis con fechas/días efectivos.
- `finance-dashboard/src/FinancialOverview.tsx` y `overview.css`: componente de Inicio sobre las API existentes. Tipografía del sistema y cifra principal editorial; tinta azul, papel cálido y acentos verde/terracota; componentes y estados con texto, no solo color. Barras sin animación; CSS respeta movimiento reducido.
- `App.tsx`: conexión con el panel existente, dirección inicial del detalle, control del contexto cargado, reintento y foco del título al navegar.
- `ForecastCard.tsx`: histórico efectivo y explicación desplegable.
- `api.ts`: tipado opcional del campo `currency` ya recibido en movimientos. Actividad reciente usa su moneda, nunca asume EUR si falta.
- Pruebas: `FinancialOverviewRegressionTest`, caso HTTP en `ServerIntegrationTest`, `test-overview-render.cjs`, `test-overview.cjs`.

No hay migraciones ni cambios de columnas. El esquema revisado usa `DECIMAL(18,2)`, fecha de operación y metadatos de clasificación; se mantienen. No cambian rutas, campos JSON de respuesta Java, roles ni permisos. Android conserva los contratos; las consultas monetariamente inválidas ahora reciben 400 con detalle en vez de un total falso. No se afirma aislamiento por propietario: no se ha modificado el modelo de acceso existente basado en roles.

## Validación realizada

| Comando | Resultado |
|---|---|
| `mvn verify` con JDK 21 completo | Primer intento: fallo de infraestructura en la prueba existente `DailyBankingSyncSchedulerTest`, que solicita Mockito INLINE y no puede auto-adjuntarse en este entorno |
| `mvn -B -DargLine=-javaagent:/home/agent/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar verify` | BUILD SUCCESS final: 178 pruebas, 171 superadas y 7 omitidas; cero fallos y errores. Servidor: 138/2 omitidas; importador: 40/5 omitidas |
| `npm test` en `finance-dashboard` | 31 comprobaciones: 26 existentes + 5 del resumen/render/monedas/contraste |
| `npm run build` en `finance-dashboard` | Correcto. Aviso de bundle >500 kB ya presente en la base |
| `git diff --check` | Sin errores |
| Playwright Inicio y movimientos | NO VERIFICADO: bloqueo de navegador, no se alcanzan los casos |

Omitidas: una prueba MySQL por falta de Docker y seis que requieren extractos privados ausentes. Resumen de resultados reales en [validation-results.txt](evidence/financial-overview/validation-results.txt).

Maven se ejecutó mediante un lanzador temporal que configura el proxy de esta sesión, JDK 21 de Microsoft y el almacén de CA del sistema. No se ha desactivado TLS ni añadido dependencias de aplicación. Las herramientas temporales no forman parte de la rama.

`npm test` valida renderizado estático y 16 combinaciones de texto/fondo con contraste calculado ≥4,5:1. No equivale a auditar estilos computados, foco o accesibilidad en navegador. Los importes sintéticos de la prueba del frontend provienen de respuestas API preparadas para la prueba; no son una integración real. La nueva prueba de Playwright está preparada para 390 y 1440 px, estados de carga/vacío/error/éxito y navegación, pero sigue sin ejecutarse.

## Revisión independiente

Árbitro separado de la implementación. Una ronda de revisión de código/SSR y dos comprobaciones dirigidas (corrección monetaria y delta final). Tres revisiones reales; ninguna ronda visual completa.

| Gravedad | Ubicación | Pasos | Esperado | Observado y resolución |
|---|---|---|---|---|
| Alto, cerrado | `FinancialOverview.tsx`, actividad reciente | Renderizar traspaso no computable −100 USD | Conservar USD y exclusión del resultado | Primer código nuevo mostraba −100 €. Corregido a US$; prueba adicional de USD y moneda ausente. Árbitro repitió `npm test` (31) y build correctamente |
| Bloqueo de validación | Entorno de navegador | Arrancar Chromium y abrir la interfaz local | Capturas reales y pruebas a 390/1440 px | `socket() Operation not permitted`; navegador remoto no alcanza el servidor local. El árbitro reprodujo el bloqueo independientemente |

No se encontraron otros críticos/altos en el código revisado; **no es aprobación visual ni E2E**. No hubo rondas visuales completas. La tercera revisión dirigida comprobó también las barras sin animación, el ajuste de texto monetario, los snapshots del ejemplo y la prueba HTTP. No se atribuye a una inspección visual.

## Capturas: objetivo pendiente

No hay capturas antes/después verificables. El Chromium instalado falla al crear sockets; el navegador remoto no puede abrir el servidor local. La instalación oficial de Headless Shell devuelve «Site Unavailable» en lugar del ZIP. No se sustituyen las capturas por imágenes generadas ni por simulaciones de navegador.

Pendiente: inspección de Inicio y detalles en 390/1440, estados interactivos, navegación por teclado, foco, ausencia de cortes, contraste computado, coherencia con pantallas vecinas y valoración estética. **El objetivo visual digno de Awwwards no está verificado.**

Para obtener evidencias en un entorno con navegador:

```bash
cd finance-dashboard
npm ci
npx playwright install chromium
npm run test:overview
npm run test:movements
```

Las capturas sintéticas de Inicio se guardarán en `docs/evidence/financial-overview`. Para el antes idéntico, crear un worktree de la base, instalar allí las dependencias y ejecutar el mismo script desde la rama nueva:

```bash
FINANCE_OVERVIEW_ROOT=/ruta/finance-suite-base/finance-dashboard npm run test:overview -- --before
```

No ejecutar `--before` contra el código modificado: se etiquetarían mal las capturas. La fecha del navegador se fija en 2026-11-09; ambos tamaños utilizan los mismos fixtures. Revisar manualmente las capturas tras ejecutar: que exista un PNG no acredita calidad visual.

## Ejecutar y revertir

Con JDK 21 completo y Maven:

```bash
mvn verify
cd finance-dashboard
npm ci
npm test
npm run build
npm run dev -- --host 127.0.0.1
```

El frontend de desarrollo necesita la API/configuración de proxy existente para datos reales; el test Playwright usa fixtures y no requiere banco. Para una restricción de auto-attach de Mockito como la observada, cargar el `mockito-core` existente mediante `-javaagent` al arrancar el JVM de pruebas, usando su ruta local real.

No se modifica producción. Reversión: volver al commit base en una rama/checkout limpio, o revertir el commit de esta rama cuando se integre. No hay rollback SQL. No reutilizar imágenes Docker con la misma etiqueta publicada; esta entrega no crea una versión ni publica imágenes.

## Pospuesto y problemas previos

- Conciliación entre importaciones y Enable Banking: siguen pudiendo proporcionar IDs diferentes. No se ha implementado deduplicación nueva por fecha/importe, que podría borrar compras legítimas.
- Importaciones y reautorización: conservadas; no se declara validación bancaria en vivo. MySQL real y muestras privadas no disponibles se contabilizan como omitidos.
- Estimación de vencimientos de tarjeta y efectivo futuro: sigue sin modelarse; la previsión se identifica como orientativa. Con snapshots antiguos o cobertura parcial puede distorsionarse.
- Soporte multimoneda, conversión trazable y revisión de formateadores de otras pantallas: fuera del alcance. La protección de esta entrega cubre los agregados modificados y la actividad de Inicio.
- `DashboardAnalysisService.overview(period, productId)` conserva el saldo líquido global cuando se filtra producto: preexistente, Inicio no aplica ese filtro; no se presenta como resuelto para otros consumidores.
- Bundle grande y dependencia de Mockito INLINE de la prueba del scheduler: previos, no errores financieros introducidos.
