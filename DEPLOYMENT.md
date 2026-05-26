# Deployment

## Objetivo

Definir como levantar y desplegar la plataforma en su fase inicial, manteniendo coherencia con la arquitectura de monolito modular, PostgreSQL y frontend desacoplado.

## Principios

- despliegue simple primero
- contenedores como camino principal de consistencia
- separar configuracion por entorno
- no introducir infraestructura excesiva antes del piloto

## Entornos previstos

### Local

Uso principal:

- desarrollo diario
- validacion de arranque
- pruebas tecnicas iniciales

Componentes:

- PostgreSQL
- backend Spring Boot
- frontend React/Vite

### Produccion inicial

Uso principal:

- piloto con uno o pocos restaurantes

Componentes:

- Nginx
- backend en contenedor
- frontend servido como assets estaticos o contenedor de desarrollo temporal
- PostgreSQL

## Estructura actual del repositorio

```text
.
├── backend/
├── frontend/
├── docker/
└── docker-compose.yml
```

## Docker Compose

El proyecto ya incluye [docker-compose.yml](/Users/angel/Desktop/Table_Planing/docker-compose.yml) con:

- `postgres`
- `backend`
- `frontend`

Objetivo del compose inicial:

- levantar el stack base sin depender del entorno local
- facilitar desarrollo y validacion

## Variables de entorno

Referencia actual en [/.env.example](/Users/angel/Desktop/Table_Planing/.env.example).

Variables principales:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`
- `BACKEND_PORT`
- `FRONTEND_PORT`
- `SPRING_PROFILES_ACTIVE`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `VITE_API_BASE_URL`
- `VITE_WS_BASE_URL`

## Entorno local

### Requisitos

- Docker
- Docker Compose
- Node.js para frontend si se quiere desarrollo local fuera de contenedores
- Java 21 y Maven si se quiere ejecutar backend fuera de Docker

Nota actual:

- en este entorno se valido mejor el backend mediante Docker porque localmente no hay `Maven` y la JVM disponible no es `Java 21`

### Arranque rapido con Docker

Base de datos solo:

```bash
docker compose up postgres
```

Stack completo:

```bash
docker compose up --build
```

### Desarrollo mixto

Base de datos:

```bash
docker compose up postgres
```

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## PostgreSQL

### Rol

PostgreSQL es la fuente principal de verdad para:

- configuracion del restaurante
- clientes
- reservas
- asignaciones
- reglas
- auditoria

### Inicializacion

Existe un script base en [docker/postgres/init.sql](/Users/angel/Desktop/Table_Planing/docker/postgres/init.sql).

### Migraciones

Las migraciones deben gestionarse con Flyway desde el backend:

- ubicacion actual: [backend/src/main/resources/db/migration](/Users/angel/Desktop/Table_Planing/backend/src/main/resources/db/migration)
- convencion: `V<number>__description.sql`

## Backend

### Tecnologia

- Java 21
- Spring Boot
- Flyway
- Spring Security
- Spring WebSocket

### Imagen

El backend dispone de [backend/Dockerfile](/Users/angel/Desktop/Table_Planing/backend/Dockerfile), que:

- compila con Maven y Java 21
- genera un jar
- ejecuta el servicio sobre JRE 21

### Configuracion

Archivos actuales:

- [backend/src/main/resources/application.yml](/Users/angel/Desktop/Table_Planing/backend/src/main/resources/application.yml)
- [backend/src/main/resources/application-dev.yml](/Users/angel/Desktop/Table_Planing/backend/src/main/resources/application-dev.yml)
- [backend/src/main/resources/application-prod.yml](/Users/angel/Desktop/Table_Planing/backend/src/main/resources/application-prod.yml)

Objetivos de configuracion:

- perfiles `dev` y `prod`
- datasource externo por variables
- Flyway activo
- logs diferenciados por entorno

## Frontend

### Tecnologia

- React
- TypeScript
- Vite
- Tailwind CSS

### Imagen

Existe [frontend/Dockerfile](/Users/angel/Desktop/Table_Planing/frontend/Dockerfile) para levantar el entorno base del frontend.

### Variables

El frontend consume:

- `VITE_API_BASE_URL`
- `VITE_WS_BASE_URL`

En la fase inicial esto permite desacoplar entorno local y despliegue futuro.

## Nginx

### Rol previsto

Nginx se recomienda para produccion inicial como:

- reverse proxy
- terminacion TLS
- encaminamiento hacia backend
- entrega de assets del frontend

### En esta fase

No es obligatorio configurar Nginx completo todavia si el objetivo es solo desarrollo y validacion tecnica, pero debe considerarse parte del despliegue inicial hacia piloto.

## Despliegue inicial recomendado

### Opcion A. VPS sencillo

Adecuado para piloto controlado:

- Docker instalado
- contenedores para backend y frontend
- PostgreSQL local o gestionado
- Nginx delante

Ventajas:

- control completo
- coste moderado

### Opcion B. Plataforma gestionada

Ejemplos:

- Render
- Railway
- Fly.io

Ventajas:

- menos carga operativa
- despliegue rapido

Consideraciones:

- revisar soporte de PostgreSQL
- revisar persistencia
- revisar networking y WebSocket

## Flujo de despliegue sugerido

1. construir imagen backend
2. construir frontend o servirlo en contenedor
3. aplicar variables de entorno
4. levantar PostgreSQL
5. arrancar backend
6. verificar migraciones Flyway
7. exponer frontend y backend via Nginx
8. comprobar health checks

## Health checks iniciales

- `GET /actuator/health`
- `GET /api/system/ping`

## Seguridad operativa basica

- no commitear secretos reales
- usar `.env` local fuera de git
- activar HTTPS en produccion
- restringir acceso a la base de datos
- hacer backups de PostgreSQL

## Backups y persistencia

Para piloto inicial se recomienda:

- volumen persistente para PostgreSQL
- backup diario
- prueba periodica de restauracion

## Observabilidad minima

- logs del backend
- logs de Nginx en produccion
- seguimiento de errores de arranque
- health checks

## Alcance de la primera fase tecnica

La primera fase debe dejar claro como:

- arrancar localmente
- levantar PostgreSQL
- ejecutar backend y frontend
- usar variables de entorno
- preparar el camino a un piloto con Nginx

No hace falta todavia:

- orquestacion Kubernetes
- autoescalado
- despliegue multi-region
