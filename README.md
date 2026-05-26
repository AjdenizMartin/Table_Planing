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

Actualmente este repositorio contiene la documentacion fundacional del proyecto. Todavia no se ha implementado codigo de aplicacion.

Ahora tambien incluye un esqueleto inicial de monorepo con:

- `backend/` para Spring Boot
- `frontend/` para React + TypeScript + Vite
- `docker-compose.yml` con PostgreSQL y servicios base
- configuracion minima para arrancar y evolucionar el sistema

## Documentacion clave

- [ARCHITECTURE.md](/Users/angel/Desktop/Table_Planing/ARCHITECTURE.md)
- [DATABASE.md](/Users/angel/Desktop/Table_Planing/DATABASE.md)
- [ALGORITHM.md](/Users/angel/Desktop/Table_Planing/ALGORITHM.md)
- [API.md](/Users/angel/Desktop/Table_Planing/API.md)
- [SECURITY.md](/Users/angel/Desktop/Table_Planing/SECURITY.md)
- [TESTING.md](/Users/angel/Desktop/Table_Planing/TESTING.md)
- [DEPLOYMENT.md](/Users/angel/Desktop/Table_Planing/DEPLOYMENT.md)
- [ROADMAP.md](/Users/angel/Desktop/Table_Planing/ROADMAP.md)
- [AGENTS.md](/Users/angel/Desktop/Table_Planing/AGENTS.md)

## Principios de construccion

- Monolito modular antes que microservicios.
- Motor determinista para asignacion de mesas.
- IA solo como soporte explicativo y analitico.
- Multi-tenant logico desde el inicio.
- Trazabilidad de decisiones y cambios.
- UX pensada para tablet y operacion real en sala.

## MVP recomendado

La primera version funcional debe incluir:

- autenticacion y roles
- configuracion del restaurante
- salones, mesas y combinaciones
- reservas manuales
- asignacion automatica inicial
- planning visual diario
- confirmaciones basicas por SMS

## Siguientes pasos

1. Crear la estructura tecnica del repositorio.
2. Inicializar backend y frontend.
3. Definir migraciones base y dominio inicial.
4. Implementar autenticacion y aislamiento por restaurante.
5. Construir configuracion de restaurante antes de desarrollar el algoritmo.

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
- En este entorno local actual no hay `Maven` instalado y la JVM disponible es `Java 19`, por lo que la forma mas estable de levantar el backend es mediante Docker o instalando Java 21 y Maven.
