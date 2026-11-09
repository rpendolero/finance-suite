# Integración del resumen financiero y sistema visual

Fecha: 9 de noviembre de 2026. Destino: `master`, rama principal real del repositorio; no existe `main`.
Origen: `improve/financial-overview`. Revisión funcional y visual de código: `f3d730e08cb42987c00033bf7f83972fc8deb40b`.
Base: `18a3e89d8d892d54ba2af7b2a57c8d93b30d6317`.

## Cambios incorporados

- Inicio distingue saldo registrado, resultado del período y previsión orientativa, con acceso a los detalles de ingresos y gastos.
- Los agregados de dashboard, resumen y previsión rechazan movimientos computables en monedas distintas de EUR en vez de mezclar importes o excluirlos silenciosamente.
- La previsión utiliza días históricos transcurridos y rechaza un histórico enteramente futuro. El selector de mes completo y los rangos personalizados dejan de diluir la media con días aún no observados.
- Tipografía común para todas las pantallas: se elimina Georgia del resultado de Inicio; textos, controles, gráficos y cifras utilizan la misma familia del sistema.
- Paleta, escala tipográfica, espaciado, radios, sombras, foco y movimiento reducido centralizados en `finance-dashboard/src/design-system.css`.
- Login, productos, movimientos, ingresos/gastos, calendario, recurrentes, Insights, revisión, categorías, bancos, importación y modales comparten las reglas visuales.
- Tarjetas de conexiones bancarias convertidas en botones nativos; se conserva su acción. Cancelar mantiene su estilo secundario en el editor del límite.
- Icono y color de interfaz coordinados con la paleta.

## Validación y revisión

Durante el desarrollo se registraron:
- Maven con agente Mockito: 178 casos, 171 superados y 7 omitidos; cero fallos/errores. Omitidos: MySQL sin Docker y seis casos que necesitan ficheros privados.
- Frontend final: 35 comprobaciones y build correctos.
- 22 pares de texto/fondo de la paleta alcanzan contraste calculado mínimo 4,5:1.
- Revisión independiente de código y comprobación dirigida: corregida una presentación de USD como EUR y la diferencia entre Cancelar/Guardar. Sin otros críticos/altos encontrados en los cambios revisados.
- La CI de la solicitud de integración ofrece el resultado actualizado del commit que se fusiona; estos resultados locales no se presentan como una nueva ejecución.

Sigue pendiente la inspección visual completa a 390/1440 px y E2E de todas las pantallas. El navegador del entorno local estaba bloqueado. El render estático, la paleta y una suite de movimientos no equivalen a esa inspección. No se declara acreditado el objetivo estético Awwwards.

## Ajuste de prueba al integrar

La primera CI de esta PR pasó el backend, las 35 comprobaciones de frontend y el build. Las primeras 15 comprobaciones de navegador pasaron; el caso de Inicio se detuvo esperando el título anterior «Evolución financiera». Se actualizan las dos esperas para comprobar que el gráfico real `.overview-history .recharts-wrapper` está visible, también tras un error de previsión. No se omite ni se relaja la comprobación del gráfico y no se modifica el código de la aplicación por este ajuste. La suite completa se vuelve a ejecutar en la CI antes de fusionar.

## Compatibilidad y alcance de la integración

Se conserva la versión 0.5.15: la orden actual autoriza fusionar, no crear una versión o etiqueta. No hay migraciones ni cambios de columnas o de los contratos JSON Java. El tipado opcional `Movement.currency` refleja el campo ya devuelto por la API. Una consulta monetariamente inválida puede devolver 400 con detalle explicable. Se conservan las reglas de Devoluciones y No computable.

Esta integración no despliega producción ni publica imágenes Docker. La importación Kutxabank ya estaba en la base mediante la PR #40 y no se duplica. Revertir el commit de fusión revierte el código; no hay rollback SQL.

Detalles y límites: [auditoría financiera](FINANCIAL-OVERVIEW-AUDIT.md), [sistema visual](VISUAL-SYSTEM.md) y [evidencias locales](evidence/financial-overview/validation-results.txt).
