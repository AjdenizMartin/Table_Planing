# Roadmap

## Estado de fases

- `Fase 1`: PARTIALLY_DONE
- `Fase 2`: PARTIALLY_DONE
- `Fase 3`: PARTIALLY_DONE
- `Fase 4`: PARTIALLY_DONE
- `Fase 5`: PARTIALLY_DONE
- `Fase 6`: PARTIALLY_DONE
- `Fase 7`: PARTIALLY_DONE
- `Fase 8`: PARTIALLY_DONE
- `Fase 9`: NOT_STARTED
- `Fase 10`: IN_PROGRESS

## Enfoque

El roadmap prioriza primero el nucleo de valor del producto: configuracion del restaurante, reservas, motor de asignacion y planning visual. Integraciones avanzadas e IA se incorporan despues de estabilizar la operativa principal.

## Fase 1. Base del proyecto y autenticacion

**Estado:** `PARTIALLY_DONE`

### Objetivos

- crear estructura tecnica inicial
- configurar backend y frontend
- establecer autenticacion y autorizacion
- preparar aislamiento multi-tenant basico

### Entregables

- repositorio estructurado
- proyecto Spring Boot base
- proyecto React base
- login
- JWT y refresh token
- roles iniciales

### Criterios de finalizacion

- un usuario autenticado puede acceder solo a sus recursos
- la app arranca en entorno local con Docker

## Fase 2. Configuracion del restaurante

**Estado:** `PARTIALLY_DONE`

### Objetivos

- modelar restaurante, salones y mesas
- permitir configuracion visual y operativa
- soportar combinaciones de mesas y reglas iniciales

### Entregables

- CRUD de restaurante
- CRUD de salones
- CRUD de mesas
- CRUD de combinaciones
- reglas operativas minimas

### Criterios de finalizacion

- un restaurante puede quedar totalmente configurado sin tocar codigo

## Fase 3. Gestion de reservas

**Estado:** `PARTIALLY_DONE`

### Objetivos

- crear reservas manuales
- gestionar clientes
- controlar estados de reservas
- validar conflictos horarios

### Entregables

- modulo de clientes
- modulo de reservas
- estados de reserva
- validacion temporal
- historial basico

### Criterios de finalizacion

- se puede operar un servicio real de forma manual sin errores graves

## Fase 4. Algoritmo basico de asignacion

**Estado:** `PARTIALLY_DONE`

### Objetivos

- asignar mesas automaticamente
- aplicar scoring serio
- soportar combinaciones validas
- explicar la decision tomada

### Entregables

- motor de candidatos
- validacion de disponibilidad
- scoring inicial
- explicacion de asignacion

### Extension avanzada prevista

- excluir mesas de almacen del algoritmo normal
- evaluar sillas extra y almacen solo en niveles avanzados con coste operativo
- generar planes de montaje y tareas antes de aplicar opciones especiales

### Criterios de finalizacion

- el sistema asigna de forma consistentemente mejor que una regla trivial

## Fase 5. Planning visual

**Estado:** `PARTIALLY_DONE`

### Objetivos

- ofrecer una vista clara del servicio diario
- permitir mover reservas manualmente
- visualizar disponibilidad y conflictos

### Entregables

- planning por hora y mesa
- filtros por salon
- acciones operativas rapidas
- drag and drop inicial

### Extension avanzada prevista

- mostrar recursos de almacen y montajes especiales como elementos operativos diferenciados
- mostrar tareas de preparacion asociadas a reservas
- permitir aprobar o rechazar opciones avanzadas sin cambiar horas existentes

### Criterios de finalizacion

- el manager puede trabajar principalmente desde el planning

## Fase 6. Tiempo real

**Estado:** `PARTIALLY_DONE`

### Objetivos

- sincronizar cambios entre dispositivos
- reflejar estado actualizado del planning

### Entregables

- WebSocket/STOMP
- eventos de reservas
- eventos de planning
- actualizacion reactiva del frontend

### Criterios de finalizacion

- cambios visibles casi en tiempo real en dos clientes conectados

## Fase 7. Confirmaciones SMS y WhatsApp

**Estado:** `PARTIALLY_DONE`

### Objetivos

- automatizar recordatorios y confirmaciones
- reducir no-shows y reservas dudosas

### Entregables

- integracion con Twilio SMS
- abstraccion de proveedor
- logs de envio
- reintentos
- base para WhatsApp

### Criterios de finalizacion

- mensajes enviados y auditables end-to-end

## Fase 8. IA y recomendaciones

**Estado:** `PARTIALLY_DONE`

### Objetivos

- añadir una capa de inteligencia explicativa
- detectar oportunidades de mejora

### Entregables

- recomendaciones de planning
- explicaciones de decisiones
- alertas de uso suboptimo

### Criterios de finalizacion

- la IA aporta contexto util sin sustituir al algoritmo

## Fase 9. Estadisticas y optimizacion avanzada

**Estado:** `NOT_STARTED`

### Objetivos

- medir rendimiento operativo
- mejorar scoring con datos historicos
- evaluar escenarios futuros

### Entregables

- metricas de ocupacion
- no-show rate
- analisis de huecos muertos
- simulaciones avanzadas

### Criterios de finalizacion

- se pueden justificar mejoras del producto con metricas

## Fase 10. Preparacion para produccion

**Estado:** `IN_PROGRESS`

### Objetivos

- endurecer la plataforma
- preparar despliegue piloto y soporte

### Entregables

- configuracion de produccion
- backups
- observabilidad
- documentacion operativa
- seguridad revisada

### Criterios de finalizacion

- el producto esta listo para un piloto real con restaurantes

## MVP recomendado

El MVP debe incluir solo lo esencial para demostrar valor real:

- autenticacion
- restaurante, salones y mesas
- combinaciones
- reservas manuales
- asignacion automatica inicial
- planning visual
- confirmacion SMS basica

## MVP avanzado recomendado

Para demostrar valor en el problema real de mesas moviles y almacen sin romper el sistema actual:

- `tableType` en mesas (`FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`)
- inventario `StorageResource` para sillas extra y mesas guardadas
- exclusion de mesas `STORAGE` del planning normal y candidatos basicos
- UI minima para ver y crear recursos de almacen
- documentacion del algoritmo por niveles antes de implementar montajes automaticos

### Sprint 1. Storage Inventory

**Estado:** `DONE`

- inventario configurable con tipos operativos, cantidad, capacidad por unidad y tiempo de preparacion
- filtros por tipo y estado
- resumen de sillas, mesas y recursos activos/inactivos
- edicion, desactivacion y reactivacion sin borrado fisico
- aislamiento por restaurante y validaciones negativas cubiertas por tests de integracion
- sin uso automatico por el algoritmo

### Sprint 2. Combinaciones Avanzadas

**Estado:** `NOT_STARTED`

- definir combinaciones avanzadas sin alterar reservas existentes
- documentar primero reglas, coste operativo y explicabilidad
- mantener fuera hasta una decision posterior los planes de montaje, tareas operativas y aprobacion de manager

Las fases posteriores siguen incluyendo planes de montaje, tareas operativas, aprobacion de manager y editor visual avanzado. No forman parte de Sprint 1.

## Fuera del MVP

- recepcion automatica desde todos los canales externos
- forecasting avanzado
- IA con replanificacion compleja
- dashboards empresariales multi-sede

## Orden sugerido de implementacion

1. documentacion y decisiones base
2. estructura de repositorio
3. backend base y seguridad
4. frontend base
5. dominio restaurante y layout
6. reservas y clientes
7. algoritmo inicial
8. planning visual
9. tiempo real
10. mensajeria
