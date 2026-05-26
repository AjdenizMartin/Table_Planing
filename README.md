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
- planning diario, tiempo real por `WebSocket` e insights operativos deterministas
- frontend `React + TypeScript` con login, configuracion, reservas y planning visual
- `docker-compose.yml` para levantar `PostgreSQL`, backend y frontend

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

## Principios de construccion

- Monolito modular antes que microservicios.
- Motor determinista para asignacion de mesas.
- IA solo como soporte explicativo y analitico.
- Multi-tenant logico desde el inicio.
- Trazabilidad de decisiones y cambios.
- UX pensada para tablet y operacion real en sala.

## MVP implementado parcialmente

La base actual ya cubre una parte relevante del MVP:

- autenticacion y roles
- configuracion del restaurante
- salones, mesas y combinaciones
- reservas manuales
- asignacion automatica inicial
- planning visual diario
- confirmaciones basicas por SMS

Pendiente para una fase mas madura:

- automatizaciones operativas mas profundas
- integraciones externas adicionales
- refinamiento del algoritmo y analitica avanzada

## Arranque rapido

### Requisitos

- Docker Desktop o daemon Docker en ejecucion
- o alternativamente `Java 21`, `Maven` y `Node.js 20+`

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
