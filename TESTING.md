# Testing

## Objetivo

Definir una estrategia de pruebas coherente con una plataforma donde la logica de negocio, el aislamiento multi-tenant y el algoritmo de asignacion son criticos.

## Principios

- testear primero la logica que aporta valor real
- combinar pruebas rapidas de unidad con pruebas de integracion realistas
- proteger especialmente permisos, tiempos y algoritmo
- evitar una suite lenta o fragil desde el inicio

## Piramide de testing recomendada

### 1. Unit tests

Para:

- utilidades puras
- reglas de validacion
- calculos temporales
- scoring del algoritmo
- resolucion de permisos aislada

Objetivo:

- alta velocidad
- feedback rapido
- cobertura fuerte de edge cases

### 2. Integration tests

Para:

- servicios de aplicacion
- repositorios JPA
- flujos HTTP con Spring Boot
- seguridad real de endpoints
- integracion con Flyway

Objetivo:

- validar cableado de modulos
- detectar errores de configuracion o persistencia

### 3. End-to-end selectivos

Para fases posteriores:

- login
- crear reserva
- ver planning
- mover reserva

No son prioridad absoluta antes de estabilizar backend y modelo de datos.

## Herramientas recomendadas

### Backend

- `JUnit 5`
- `Spring Boot Test`
- `MockMvc`
- `Spring Security Test`
- `Testcontainers`
- `AssertJ`

### Frontend

Fase inicial:

- no forzar una suite grande aun

Fase posterior sugerida:

- `Vitest`
- `React Testing Library`
- `Playwright` para flujos criticos

## Testcontainers

Se recomienda usar `Testcontainers` desde fases tempranas para:

- PostgreSQL real
- Redis si se incorpora en pruebas relevantes

Beneficios:

- pruebas mas fieles que mocks de base de datos
- validacion real de migraciones Flyway
- menor riesgo de diferencias entre local y CI

## Tipos de pruebas por modulo

### Auth y Security

Casos importantes:

- login exitoso
- login fallido
- refresh valido
- refresh invalido o revocado
- acceso a endpoint protegido sin token
- acceso con rol insuficiente
- acceso a recurso de otro restaurante

### Restaurant, DiningRoom y Table

Casos importantes:

- crear restaurante valido
- evitar duplicados prohibidos
- crear salon y mesa con capacidades validas
- impedir editar recursos de otro restaurante

### Reservation

Casos importantes:

- crear reserva valida
- rechazar reserva con datos invalidos
- detectar solapamiento segun ventana efectiva
- cambiar estados correctamente
- cancelar y liberar recurso asociado

### Planning

Casos importantes:

- devolver planning del dia por restaurante
- aislar salones correctamente
- recalculo restringido a roles permitidos

### Notification

Casos importantes:

- registrar log de envio
- reintento segun estado
- no exponer datos sensibles a roles no permitidos

### AI

Casos importantes:

- generar recomendacion explicativa desde datos validos
- no permitir que la IA actue como fuente primaria de asignacion

## Testing del algoritmo

Este es uno de los puntos mas importantes del producto.

### Objetivos del testing del algoritmo

- validar restricciones duras
- proteger el scoring
- asegurar decisiones coherentes
- evitar regresiones al retocar pesos o reglas

### Escenarios minimos recomendados

- reserva de 2 no ocupa mesa de 6 si existe mesa adecuada
- no se asigna mesa con conflicto horario
- se respeta buffer de limpieza
- se evita salon no prioritario si no hace falta
- se respeta accesibilidad
- se penaliza bloqueo de mesa grande
- se detecta hueco muerto relevante
- recolocacion de un salto mejora el resultado

### Enfoque

- tests de unidad para funciones de score y filtros
- tests de integracion para escenarios completos de planning
- datasets pequeños y legibles

## Tests de API

Se recomienda cubrir:

- status codes correctos
- validaciones de payload
- serializacion de fechas
- contratos base de respuesta
- errores controlados

Casos:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/restaurants`
- `POST /api/reservations`
- `GET /api/planning`

## Tests de permisos

Deben ser explicitos y no quedar implicitos dentro de otros tests.

Casos minimos:

- `WAITER` no puede editar configuracion
- `MANAGER` puede crear reservas
- `RESTAURANT_OWNER` puede editar su restaurante
- usuario de restaurante A no puede acceder a datos de restaurante B
- `PLATFORM_ADMIN` puede operar globalmente segun politica

## Tests de WebSocket

No son prioridad en la primera linea de codigo, pero deben planificarse.

Cobertura sugerida:

- conexion autenticada
- suscripcion por restaurante
- emision de evento al crear reserva
- emision de evento al cancelar reserva
- no recibir eventos de otro restaurante

## Estrategia de datos de prueba

- factories o builders para entidades
- fixtures pequenas y legibles
- evitar dependencias ocultas entre tests
- cada test debe preparar su propio contexto o uno controlado

## CI recomendada

Pipeline inicial:

1. validar formato y compilacion
2. ejecutar unit tests
3. ejecutar integration tests
4. publicar reporte

Cuando el proyecto madure:

5. ejecutar e2e criticos

## Definition of done para testing

Antes de cerrar una funcionalidad importante:

- existe cobertura de casos felices
- existen casos de error relevantes
- se han probado permisos
- se han probado restricciones multi-tenant
- si afecta algoritmo, hay casos de no regresion

## Alcance de la primera fase tecnica

La primera fase debe dejar preparados:

- tests de contexto Spring
- tests basicos de seguridad
- primer setup de `Testcontainers` para PostgreSQL
- base para `MockMvc`

No hace falta aun:

- suite e2e completa
- cobertura exhaustiva del frontend
- simulaciones avanzadas del algoritmo
