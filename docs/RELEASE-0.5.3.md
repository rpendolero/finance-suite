# Finance Suite 0.5.3

Base: 0.5.2.

- El selector superior añade Enero–Diciembre y un selector de año. Cada mes abarca el intervalo completo y respeta los años bisiestos.
- Se conservan Este mes, Mes anterior, Últimos 3 meses, Últimos 6 meses, Este año y Personalizado.
- El calendario muestra Lunes–Domingo en español y posiciona las fechas bajo su día de la semana, conservando los huecos al comienzo del período.
- Cada fecha muestra día y mes, operaciones, ingresos y gastos. En móvil se mantienen las siete columnas mediante desplazamiento horizontal.
- Los cálculos del calendario conservan la posición al cambiar de mes, año y horario de verano.
- Maven, el dashboard, el bridge MCP y los scripts de arranque utilizan la versión 0.5.3. El dashboard y el servidor MCP obtienen la versión de sus descriptores de construcción.
- La CI utiliza una caché de Docker Hub y fuentes alternativas para descargar las imágenes de Testcontainers cuando un registro limita las descargas.

No hay cambios en el modelo de datos ni nuevas migraciones. Para ver las mejoras, reconstruye y despliega el frontend. Si reconstruyes los módulos Java, los JAR se generan con el sufijo 0.5.3.

Validación del frontend: 20 comprobaciones y compilación de producción.
