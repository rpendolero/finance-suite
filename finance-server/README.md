# Finance Server 0.2.2

Aplicación de explotación de datos de Finance Suite. Se ejecuta separada del importador local y no incluye Playwright. Compila desde la raíz del proyecto: `mvn clean install`.

Arranque, importador, permisos y contrato de comunicación: [../README.md](../README.md).

- API REST `/api` y 18 herramientas MCP `/mcp`.
- Ingestión autenticada `POST /api/importer/products/{id}/batches`.
- MySQL 8.4 y migraciones Flyway conservadas.
- Adaptadores REST/MCP sobre los mismos casos de uso.
- Credenciales reader, importer y admin con permisos separados.

No hay endpoint para abrir un navegador o solicitar descarga bancaria. Ejecuta la importación en el equipo local con `finance-importer`.

Documentos: [docs/OPERATIONS.md](docs/OPERATIONS.md), [docs/OPENCLAW.md](docs/OPENCLAW.md), [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
