# Roadmap

## Enfoque

El roadmap prioriza primero el nucleo de valor del producto: configuracion del restaurante, reservas, motor de asignacion y planning visual. Integraciones avanzadas e IA se incorporan despues de estabilizar la operativa principal.

## Fase 1. Base del proyecto y autenticacion

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

### Criterios de finalizacion

- el sistema asigna de forma consistentemente mejor que una regla trivial

## Fase 5. Planning visual

### Objetivos

- ofrecer una vista clara del servicio diario
- permitir mover reservas manualmente
- visualizar disponibilidad y conflictos

### Entregables

- planning por hora y mesa
- filtros por salon
- acciones operativas rapidas
- drag and drop inicial

### Criterios de finalizacion

- el manager puede trabajar principalmente desde el planning

## Fase 6. Tiempo real

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
