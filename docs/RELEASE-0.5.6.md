# Finance Suite 0.5.6 — Acceso al dashboard

## Cambios

- **Usuario:** el login ya no precarga `reader`; el campo se muestra vacío.
- **Contraseña:** oculta al abrir el formulario; el icono de ojo permite verla u ocultarla sin enviar el login.
- **Accesibilidad:** controles etiquetados y estilos del botón independientes del botón «Iniciar sesión».
- **Compatibilidad:** no se han modificado contratos REST/MCP, lógica de autenticación del servidor ni esquema de MySQL.

## Componentes

- Maven: `finance-suite`, `finance-domain`, `finance-statements`, `finance-server` y `finance-importer`: **0.5.6**.
- Frontend React: `finance-dashboard/package.json` y `package-lock.json`: **0.5.6**.
- Puente Node del servidor: `finance-server/package.json` y `package-lock.json`: **0.5.6**.
- Scripts de arranque Java y ejemplos Docker actualizados a **0.5.6**.

## Validación recomendada

```bash
mvn -B clean verify
cd finance-dashboard
npm ci
npm test
npm run build
```

Comprobar manualmente al desplegar: usuario inicialmente vacío, contraseña enmascarada, mostrar/ocultar sin enviar el formulario e inicio de sesión válido con las credenciales habituales.

## Publicación

```bash
./scripts/publish-docker.sh 0.5.6 TU_USUARIO_DOCKERHUB
```

La actualización del repositorio no publica imágenes Docker por sí misma. Reconstruir y desplegar el frontend para ver el cambio de login. No se requieren nuevas migraciones de base de datos por esta versión.
