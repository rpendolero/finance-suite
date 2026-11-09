# Sistema visual común de Finance Suite

Revisión del 9 de noviembre de 2026 sobre `improve/financial-overview`. No cambia la versión ni los contratos de API.

## Problema corregido

Inicio usaba una fuente Georgia para el resultado, además de la familia del resto de la interfaz. Sus colores tenían variables locales, mientras las demás páginas conservaban una paleta azul y tamaños/radios de revisiones anteriores. Algunos botones dependían de la fuente predeterminada del navegador. Se corrigen esas divergencias en el código de toda la aplicación.

| Antes | Después |
|---|---|
| Resultado de Inicio en Georgia y resto de textos en sans serif | Una familia del sistema para texto, títulos, controles, gráficos e importes |
| Paletas distintas en Inicio, Productos, bancos y movimientos | Una paleta definida en `design-system.css` y utilizada por las hojas de todas las páginas |
| Importes con distintos pesos y fuentes | Cifras tabulares; tamaños según importancia, manteniendo la misma familia |
| Botones y campos de distintas alturas, radios y fuentes | Tipografía heredada, radio común y controles principales de al menos 44 px |
| Estilos en línea en Bancos | Clases compartidas y tarjetas de conexión como botones nativos de teclado |
| Prueba de contraste con colores históricos codificados en el test | Prueba de 22 pares basada en los tokens realmente declarados en la paleta actual |

## Identidad y mantenimiento

`finance-dashboard/src/design-system.css` define la familia `system-ui` con alternativas del sistema, escala tipográfica, pesos, números, colores semánticos, espaciado, radios, sombras y reglas de foco/movimiento reducido. No se cargan fuentes remotas ni dependencias nuevas.

Colores principales: tinta `#18323b`, superficie de marca `#16333d`, primario/ingreso `#087566` y gasto/error `#a8412c`. Los fondos neutros, las advertencias y la previsión tienen roles definidos dentro de esa misma paleta. Los estados mantienen sus etiquetas; no dependen únicamente del color.

El mayor tamaño de una cifra o título expresa jerarquía, no otra fuente. El único tratamiento monoespaciado se reserva a ejemplos de fichero CSV en bloques `pre`, no a los textos de la interfaz.

La entrada común es `styles.css`: importa una sola vez `design-system.css` y `overview.css`. Inicio ya no importa otra hoja por su cuenta. Los componentes conservan sus distribuciones y sus reglas responsive; las propiedades visuales usan los mismos tokens.

El icono de Finance Suite conserva su geometría y utiliza colores de la paleta. `theme-color` coincide con la superficie de marca. Los logotipos de entidades bancarias conservan su identidad propia.

## Cobertura del cambio

| Pantalla o elemento | Aplicación del sistema común |
|---|---|
| Login y comprobación de sesión | Tipografía, fondo de marca, tarjeta, campos, contraseña y botón |
| Navegación y cabecera | Familia, colores, selección activa, filtros de período, salida y foco |
| Inicio | Saldo, resultado, categorías, evolución, previsión, cuentas y actividad sin Georgia ni paleta local |
| Ingresos y gastos | Cabecera, selector, totales, categorías, listados y estados |
| Productos | Cabecera, resúmenes, tarjetas, crédito, límite y acciones |
| Movimientos | Búsqueda, filtros, selección, filas, paginación y editor |
| Recurrentes e Insights | Cabeceras, tarjetas, avisos e importes |
| Calendario | Cabecera, días, importes, estados y foco |
| Revisar | Cabecera, clasificador, mensajes y acciones |
| Categorías | Formularios, códigos con fuente UI, filas, botones y campos |
| Bancos | Resumen, lista, logotipos, estados, detalle y progreso |
| Importar | Formulario, selección de fichero, ayuda, errores y resultado |
| Modales | Producto, edición, clasificación masiva, resumen de movimientos y conexiones |

## Verificación y límites

- `npm test`: 35 comprobaciones (26 existentes de editor/contratos, 4 de render de Inicio y 5 del sistema visual).
- `npm run build`: TypeScript y bundle de producción correctos; permanece el aviso previo de tamaño de chunk superior a 500 kB.
- `npm run test:design`: comprueba la familia única, herencia de controles y números tabulares; las referencias a tokens y la ausencia de colores locales; 22 pares de contraste de texto normal ≥4,5:1; entrada CSS compartida, gráficos e icono; y reglas comunes de foco y movimiento reducido.
- `git diff --check`: sin errores de espacios.

Estas comprobaciones son de fuente, paleta y render estático. No son mediciones de los estilos computados ni pruebas de interacción en un navegador. No se ha repetido Maven porque esta iteración no modifica Java, cálculos, persistencia, contratos ni configuración del servidor.

**Pendiente de validar visualmente:** todas las pantallas y modales en escritorio de 1440 px y móvil de 390 px, estados de carga/vacío/error/éxito, tabulación y ausencia de acciones cortadas. El navegador de este entorno continúa bloqueado; no hay nuevas capturas verificadas. No se declara cumplido el objetivo estético de referencia Awwwards.

Para ejecutar las pruebas de interacción en una máquina con Chromium disponible:

```bash
cd finance-dashboard
npm ci
npx playwright install chromium
npm run test:overview
npm run test:movements
```

Esas dos suites cubren Inicio y los flujos de movimientos; no sustituyen la inspección manual del resto de pantallas. No se presentan fixtures como una integración bancaria real.

## Revisión y reversión

Se realizó una ronda independiente de código/cascada y una comprobación dirigida posterior. Hallazgo medio: en Productos → tarjeta → Editar límite, el botón «Cancelar» heredaba el fondo primario del nuevo estilo base y parecía otra acción Guardar. Se esperaba un secundario claro; se corrigió la regla común `button.secondary` y el borde del grupo del editor. El árbitro confirmó el cierre y repitió las 35 comprobaciones, build y `git diff --check` correctamente. No identificó críticos/altos en el diff revisado.

La evaluación fue de código y cascada, sin navegador. No acredita estética, layout 390/1440 ni navegación real por teclado.

Para probar el aspecto en local, actualizar la rama `improve/financial-overview`, ejecutar `npm ci` y `npm run dev` en `finance-dashboard`, con la API habitual en `http://localhost:8081`. Si Vite ya estaba abierto, reiniciarlo tras actualizar.

Reversión: revertir el commit de esta iteración o volver a la revisión anterior en un checkout limpio. No hay migración ni rollback de datos. No se publica producción, no se fusiona `master` y no se crean etiquetas o versiones.
