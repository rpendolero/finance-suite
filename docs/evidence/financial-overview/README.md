# Evidencias visuales pendientes

No se obtuvieron capturas reales: Chromium falla con `socket() Operation not permitted` y el navegador remoto no puede acceder al servidor local.

`finance-dashboard/scripts/test-overview.cjs` prepara la aplicación existente con respuestas API sintéticas y fecha 2026-11-09. Está pendiente de ejecución e inspección; no acredita por sí solo accesibilidad o calidad visual.

Desde `finance-dashboard`, ejecutar `npm run test:overview` con Playwright/Chromium disponible para generar `after-1440.png`, `after-390.png` y estados de carga, error y vacío. Para las capturas anteriores, usar el mismo script con `FINANCE_OVERVIEW_ROOT` apuntando a un worktree de `18a3e89` y `--before`. No hay datos reales ni servicios bancarios en estos fixtures.
