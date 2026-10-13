# Finance Suite 0.5.7 — Unificación visual y productos financieros

## Cambios

- Rediseño de las pantallas del dashboard para mantener una identidad visual coherente con Productos.
- Nuevo logotipo en el login, la navegación y el favicon.
- Tarjetas de crédito: visualización del crédito disponible **estimado** como límite configurado menos gastos del período seleccionado.
- Posibilidad de introducir el límite al crear una tarjeta de crédito y modificarlo desde Productos.
- Botón de cierre de sesión con icono y color diferenciado.
- Corrección de sintaxis JSX en la visualización de tarjetas.

## Consideraciones

- El disponible mostrado depende del período seleccionado y **no representa necesariamente el crédito disponible bancario real**; quedan pendientes los ajustes por liquidaciones, devoluciones y operaciones no contabilizadas.
- Sin nuevas migraciones de base de datos.
- Compilación, pruebas y despliegue pendientes de verificar.
