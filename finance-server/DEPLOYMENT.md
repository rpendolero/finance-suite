# Construir y desplegar la API

El contexto de Docker debe ser la raíz del repositorio, donde están pom.xml, finance-domain y finance-server. El Dockerfile elimina finance-importer del reactor de la etapa de construcción: no necesita ese directorio.

Desde la raíz de finance-suite:

```bash
docker build -t finance-api -f finance-server/Dockerfile .
```

Desde finance-server, el contexto es el directorio padre:

```bash
docker build -t finance-api -f Dockerfile ..
```

En IntelliJ, configura Dockerfile = finance-server/Dockerfile y Context folder = raíz de finance-suite. No uses finance-server como contexto. Si falta finance-domain, descarga también ese módulo.

El JAR se copia sin fijar su número de versión. La API escucha en 0.0.0.0 dentro del contenedor y utiliza un usuario sin privilegios.

Para MySQL en otro servidor, crea un fichero api.env local (no lo subas a GitHub):

```env
DB_URL=jdbc:mysql://IP_BBDD:3306/finance?connectionTimeZone=UTC
DB_USER=finance
DB_PASSWORD=TU_PASSWORD
FINANCE_READER_PASSWORD=PASSWORD_READER
FINANCE_ADMIN_PASSWORD=PASSWORD_ADMIN
FINANCE_IMPORTER_PASSWORD=PASSWORD_IMPORTER
FINANCE_ALLOWED_ORIGINS=https://finances.myaihome.es
```

Usa contraseñas distintas para cada rol, de al menos 20 caracteres. Añade tus variables ENABLE_BANKING_* si utilizas esa integración.

```bash
docker run -d --name finance-api --restart unless-stopped \
  --env-file api.env -p 127.0.0.1:8081:8081 finance-api
```

Para conectar frontend y API por nombre, utiliza una red Docker común: el nombre finance-api se corresponde con API_UPSTREAM=http://finance-api:8081. En ese caso no hace falta publicar el puerto de la API. Mantén la configuración de tu MySQL externo en api.env.
