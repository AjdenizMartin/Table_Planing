# AGENTS

## Proposito

Este documento define como deben colaborar agentes de desarrollo sobre este proyecto, especialmente en fases tempranas donde la arquitectura, el modelo de datos y el algoritmo son mas importantes que la velocidad de implementar pantallas o integraciones secundarias.

## Objetivo del proyecto

Construir una aplicacion profesional de reservas y planificacion inteligente de mesas para restaurantes, con capacidad de evolucionar a producto comercializable multi-restaurante.

## Prioridades del equipo

1. Proteger la calidad del dominio y del algoritmo.
2. Mantener coherencia entre backend, frontend y base de datos.
3. Evitar complejidad innecesaria en etapas tempranas.
4. Documentar decisiones importantes antes de escalar implementacion.

## Reglas de trabajo

### 1. No introducir microservicios prematuramente

La base del sistema sera un monolito modular. No fragmentar servicios salvo decision explicita documentada.

### 2. No sustituir el algoritmo con IA

La IA puede explicar, resumir o sugerir. Nunca debe ser la fuente primaria de asignacion de mesas.

### 3. Todo modulo de negocio debe respetar multi-tenant

Las entidades de negocio deben incluir `restaurant_id` cuando aplique. Las consultas y permisos deben filtrar por restaurante.

### 4. No implementar flujos sin trazabilidad

Cambios de estado importantes deben dejar rastro:

- quien hizo la accion
- cuando
- sobre que entidad
- con que contexto

### 5. Priorizar explicabilidad

Cada decision automatica relevante debe poder justificarse.

### 6. No codificar reglas duras en UI

La logica de negocio y validacion vive en backend. El frontend debe consumir capacidades y reglas, no inventarlas.

## Orden recomendado de implementacion

1. documentacion fundacional
2. estructura del repositorio
3. modelo de datos y migraciones
4. autenticacion y permisos
5. configuracion de restaurante
6. reservas y clientes
7. algoritmo de asignacion
8. planning visual
9. tiempo real
10. integraciones externas

## Convenciones de arquitectura

### Backend

- Java 21
- Spring Boot
- modularizacion por dominio
- servicios de aplicacion pequenos y claros
- entidades y repositorios por modulo
- reglas de negocio fuera de controladores

### Frontend

- React con TypeScript
- estructura por features
- server state con TanStack Query
- UI orientada a tablet y operacion rapida

### Base de datos

- PostgreSQL como fuente de verdad
- Flyway para migraciones
- `jsonb` solo cuando aporte flexibilidad real

## Convenciones de cambios

- no mezclar refactors grandes con nuevas features si puede evitarse
- no romper documentos base sin actualizar referencias relacionadas
- si cambia el algoritmo, actualizar tambien `ALGORITHM.md`
- si cambia el modelo de datos, actualizar `DATABASE.md`
- si cambia una decision estructural, actualizar `ARCHITECTURE.md`

## Convenciones de calidad

- preferir cambios pequeños y revisables
- escribir tests para logica critica
- proteger especialmente calculos temporales y de asignacion
- medir impacto de cambios en permisos y multi-tenant

## Riesgos a vigilar

- algoritmo trivial que no aporte valor real
- reglas contradictorias sin validacion
- solapamientos de reservas por errores temporales
- UI de planning demasiado compleja demasiado pronto
- dependencia excesiva de integraciones externas

## Definition of done orientativa

Un cambio esta realmente terminado cuando:

- cumple la necesidad funcional
- respeta el aislamiento por restaurante
- mantiene trazabilidad
- incluye validaciones razonables
- actualiza documentacion relevante si cambia diseño

## Documentos de referencia obligatorios

- [README.md](./README.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [DATABASE.md](./DATABASE.md)
- [ALGORITHM.md](./ALGORITHM.md)
- [API.md](./API.md)
- [SECURITY.md](./SECURITY.md)
- [TESTING.md](./TESTING.md)
- [DEPLOYMENT.md](./DEPLOYMENT.md)
- [ROADMAP.md](./ROADMAP.md)

## Instruccion final para agentes

Antes de implementar cualquier modulo importante, revisar si la decision ya esta definida en estos documentos. Si no lo esta, documentarla primero o dejar una decision explicita pendiente.
