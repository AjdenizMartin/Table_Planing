# Restaurant Table Planning

Plataforma profesional de reservas y planificacion inteligente de mesas para restaurantes.

## Vision

Este proyecto busca resolver un problema operativo real: muchos sistemas de reservas aceptan solicitudes, pero asignan mesas de forma demasiado simple. Eso provoca mesas vacias, mal uso de mesas grandes, poca flexibilidad para reservas futuras y un planning diario suboptimo.

Restaurant Table Planning se plantea como un producto comercializable para restaurantes que necesitan:

- Gestionar reservas de forma centralizada.
- Configurar salones, mesas y combinaciones sin tocar codigo.
- Asignar automaticamente la mejor mesa o combinacion de mesas.
- Mantener un planning visual y operativo en tiempo real.
- Mejorar ocupacion, rotacion y calidad del servicio.

## Objetivo del producto

La aplicacion no debe limitarse a guardar reservas. Debe funcionar como un motor de decision que evalua cada nueva reserva dentro del contexto completo del servicio:

- numero de comensales
- hora y duracion estimada
- tiempo de limpieza
- mesas libres y combinaciones posibles
- reglas del restaurante
- impacto sobre reservas futuras
- riesgo de generar huecos muertos
- accesibilidad y preferencias

## Propuesta de valor

- Mejor uso del espacio del restaurante.
- Menos desperdicio de capacidad.
- Mas control operativo para managers y personal de sala.
- Configuracion flexible desde tablet.
- Base tecnologica preparada para crecer a multi-restaurante.

## Stack objetivo

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Spring WebSocket
- Spring Scheduler
- Spring AI
- Flyway

### Base de datos e infraestructura

- PostgreSQL
- Redis opcional y recomendado
- Docker
- Docker Compose
- Nginx

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- dnd-kit
- PWA

### Integraciones previstas

- Twilio SMS
- WhatsApp Cloud API
- futuros canales web y social

## Estado actual

El proyecto ya no esta solo en fase documental. Actualmente incluye una primera base funcional con:

- backend `Spring Boot` con autenticacion JWT, multi-tenant y modulos iniciales
- configuracion de restaurante, salones, mesas y combinaciones
- clientes, reservas manuales y asignacion automatica inicial
- combinaciones avanzadas con inventario, top 3 manual y aplicacion transaccional
- planning diario, tiempo real por `WebSocket` e insights operativos deterministas
- frontend `React + TypeScript` con login, configuracion, reservas y planning visual
- CI con tests backend, PostgreSQL, frontend, Playwright e imagenes Docker
- Compose local y productivo con Nginx, HTTPS, secretos, backups y health checks

## Documentacion clave

- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [DATABASE.md](./DATABASE.md)
- [ALGORITHM.md](./ALGORITHM.md)
- [API.md](./API.md)
- [SECURITY.md](./SECURITY.md)
- [TESTING.md](./TESTING.md)
- [DEPLOYMENT.md](./DEPLOYMENT.md)
- [ROADMAP.md](./ROADMAP.md)
- [AGENTS.md](./AGENTS.md)
- [docs/PROJECT_AUDIT.md](./docs/PROJECT_AUDIT.md)
- [docs/TECHNICAL_TODO.md](./docs/TECHNICAL_TODO.md)
- [docs/PLANNING_PANEL_VISION.md](./docs/PLANNING_PANEL_VISION.md)
- [docs/PLANNING_PANEL_IMPLEMENTATION_PLAN.md](./docs/PLANNING_PANEL_IMPLEMENTATION_PLAN.md)
- [docs/PILOT_RUNBOOK.md](./docs/PILOT_RUNBOOK.md)
- [docs/PILOT_UAT_CHECKLIST.md](./docs/PILOT_UAT_CHECKLIST.md)

## Principios de construccion

- Monolito modular antes que microservicios.
- Motor determinista para asignacion de mesas.
- IA solo como soporte explicativo y analitico.
- Multi-tenant logico desde el inicio.
- Trazabilidad de decisiones y cambios.
- UX pensada para tablet y operacion real en sala.

## MVP operativo implementado

La base actual ya cubre una parte relevante del MVP:

- autenticacion y roles
- configuracion del restaurante
- salones, mesas y combinaciones
- reservas manuales
- asignacion automatica inicial
- planning visual diario
- confirmaciones basicas por SMS
- sugerencias avanzadas aprobadas por manager con inventario reservado
- historial y explicacion de asignaciones

Pendiente para una fase mas madura:

- automatizaciones operativas mas profundas
- integraciones externas adicionales
- refinamiento del algoritmo y analitica avanzada
- carga de datos y UAT en el VPS real del piloto

## Arranque rapido

### Requisitos

- Docker Desktop o daemon Docker en ejecucion
- o alternativamente `Java 21`, `Maven` y `Node.js 20+`

### Estado implementado vs planificado

Implementado actualmente:

- autenticacion JWT con refresh token
- restaurante, salones, mesas y combinaciones
- clientes y reservas manuales
- planning diario
- asignacion automatica determinista
- eventos realtime
- notificaciones internas y SMS basicos
- insights explicativos de planning
- top 3 manual con recursos, coste y preparacion
- tests Vitest/Testing Library y Playwright a 768/1024 px

Planificado o parcial:

- disponibilidad dedicada como modulo/API separada
- simulacion de planning
- drag and drop avanzado
- WhatsApp y canales externos
- Redis
- Spring AI / proveedor externo de IA

### URLs locales esperadas

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Healthcheck: `http://localhost:8080/actuator/health`
- Ping tecnico: `http://localhost:8080/api/system/ping`

## Estructura del repositorio

```text
.
├── backend/
├── frontend/
├── docker/
├── README.md
├── ARCHITECTURE.md
├── DATABASE.md
├── ALGORITHM.md
├── API.md
├── SECURITY.md
├── TESTING.md
├── DEPLOYMENT.md
├── ROADMAP.md
└── AGENTS.md
```

## Demo local

La demo local queda preparada para entrar directamente con un usuario propietario y datos operativos minimos. En perfil `dev`, el backend crea de forma idempotente el restaurante demo, salones, mesas, combinaciones, clientes y reservas del dia.

### Credenciales demo

- email: `demo@restaurant.com`
- password: `Demo1234!`
- rol: `RESTAURANT_OWNER`
- restaurante: `Demo Restaurant`

### Datos demo incluidos

- Salones: `Main Dining Room`, `Side Dining Room`, `Upper Dining Room`
- Mesas: `Table 1` a `Table 7` con capacidades de 2, 4 y 6 comensales
- Combinaciones: `Table 1 + Table 2` y `Table 3 + Table 4`
- Clientes: `John Smith`, `Maria Garcia`, `David Murphy`
- Reservas del dia actual con estados mezclados entre pendientes y confirmadas

### Opcion 1. Solo base de datos con Docker

```bash
docker compose up postgres
```

Este modo es util si quieres arrancar el backend desde tu maquina. PostgreSQL se usa internamente en Docker Compose; si quieres conectar herramientas externas al puerto 5432, revisa primero que ese puerto no este ocupado en tu equipo.

### Opcion 2. Stack completo con Docker

```bash
docker compose up --build
```

Despues abre:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Ping: `http://localhost:8080/api/system/ping`

### Opcion 3. Desarrollo mixto

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

Para desarrollo mixto, el frontend usa `VITE_API_BASE_URL=http://localhost:8080`. Si el backend no responde, el login mostrara un mensaje indicando que revise Docker, el puerto 8080 o CORS.

### Errores comunes

- Si Docker indica que un puerto esta ocupado, libera el contenedor/proceso que lo usa o cambia el puerto en un archivo `.env` local.
- Si el login falla con credenciales incorrectas, usa exactamente `demo@restaurant.com` y `Demo1234!`.
- Si el navegador muestra error de CORS, confirma que el backend esta arrancado y que el frontend se abre desde `http://localhost:5173`.
- Si `health` aparece `DOWN`, revisa los logs del backend y la conexion con PostgreSQL.
- Si ya habias probado datos antiguos y quieres una demo limpia, puedes recrear la base local con `docker compose down -v` y despues `docker compose up --build`. Esto elimina los datos locales del volumen Docker.

## Arranque base

### Opcion 1. Solo base de datos con Docker

```bash
docker compose up postgres
```

### Opcion 2. Stack completo con Docker

```bash
docker compose up --build
```

Si Docker no esta arrancado, este comando fallara. En macOS eso suele significar que Docker Desktop no esta abierto o el socket Docker no esta disponible.

Credenciales demo locales en perfil `dev`:

- email: `demo@restaurant.com`
- password: `Demo1234!`

### Opcion 3. Desarrollo mixto

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

## Notas de entorno

- El backend esta configurado para `Java 21`.
- La validacion reciente del proyecto confirma:
  - `mvn -q -DskipTests package` correcto en `backend`
  - `npm run build` correcto en `frontend`
- Si el proyecto no arranca localmente, revisa primero que Docker Desktop este levantado.
- PostgreSQL no necesita exponerse al host para que la aplicacion funcione dentro de Docker Compose.
