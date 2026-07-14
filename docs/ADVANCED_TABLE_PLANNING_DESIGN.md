# Advanced Table Planning Design

## 1. Problema operativo real

Muchos restaurantes no operan con un plano completamente fijo. Durante un servicio real, el equipo puede juntar mesas, mover mesas ligeras, añadir sillas, sacar mesas guardadas o preparar un montaje temporal para grupos grandes. Las apps de table planning que solo buscan una mesa libre o una combinacion simple suelen fallar en estos casos:

- dejan mesas vacias aunque exista una solucion operativa viable
- bloquean mesas grandes para reservas pequenas
- no entienden que una mesa puede estar fisicamente en almacen y no en el salon
- no distinguen entre una combinacion normal y un montaje que requiere trabajo del staff
- no avisan al equipo de preparacion cuando la asignacion requiere accion previa
- optimizan capacidad sin valorar coste operativo, accesibilidad o impacto futuro

El resultado es peor ocupacion, mas improvisacion durante el servicio, reservas grandes rechazadas innecesariamente y mas carga mental para managers y camareros.

## 2. Objetivo del sistema avanzado

El objetivo es evolucionar el producto hacia un motor de planificacion diaria que entienda recursos fisicos y coste operativo, sin perder explicabilidad ni control humano. El sistema debe poder:

- partir de un layout base del restaurante
- generar o editar un plano diario por servicio
- distinguir mesas fijas, moviles, temporales y almacenadas
- modelar sillas extra y otros recursos guardados
- evaluar combinaciones normales y avanzadas
- proponer montajes especiales cuando compense
- crear tareas operativas para preparar esos montajes
- explicar por que una opcion es barata, cara, segura o arriesgada

Regla central: el sistema no debe cambiar la hora de una reserva existente. Puede moverla entre recursos, unir mesas o sugerir preparaciones, pero la hora solo cambia si el cliente lo solicita y el staff la edita manualmente. Para una nueva solicitud, el sistema puede sugerir horas alternativas si no hay opcion viable a la hora pedida.

## 3. Conceptos principales

### Base layout

Configuracion estable del restaurante: salones, mesas habituales, coordenadas, capacidades, accesibilidad y prioridades. Representa como suele estar montado el restaurante antes de ajustes diarios.

### Daily floor plan

Version operativa de un dia o turno concreto. Puede copiar el base layout y aplicar excepciones: mesas bloqueadas, mesas movidas, recursos de almacen sacados, zonas abiertas o cerradas y montajes temporales.

### Fixed tables

Mesas que normalmente no se mueven. Pueden formar parte de combinaciones, pero moverlas debe estar prohibido o tener un coste muy alto.

### Movable tables

Mesas presentes en el salon que el staff puede mover o juntar. Tienen menor coste operativo que usar almacen, pero aun asi requieren tiempo y coordinacion.

### Storage tables

Mesas guardadas fuera del salon. No deben aparecer como mesas normales disponibles en el planning. Solo entran en juego mediante una opcion avanzada o un montaje aprobado.

### Extra chairs

Sillas adicionales disponibles en almacen o back office. Permiten ampliar capacidad de una mesa o montaje, con limites fisicos y operativos.

### Table combinations

Combinaciones configuradas de mesas existentes. En fase inicial son combinaciones estandar del salon. En fases avanzadas pueden incluir coste, restricciones, orientacion y requisitos de preparacion.

### Setup options

Opciones configurables o generadas que describen como atender una reserva: mesa individual, combinacion, mesa con sillas extra, mesa de almacen, montaje temporal o combinacion con movimiento.

### Reservation setup plans

Plan elegido o propuesto para una reserva concreta. Debe indicar recursos usados, coste, aprobacion requerida, explicacion y estado.

### Setup tasks

Tareas operativas derivadas de un plan: sacar mesa del almacen, anadir 4 sillas, mover dos mesas, preparar mantel grande, abrir salon secundario o confirmar con manager.

## 4. Modelo de datos propuesto

### Cambios a RestaurantTable

Campos propuestos:

- `table_type`: `FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`
- `dining_room_id`: nullable para `STORAGE`; obligatorio para mesas fisicas en salon
- `movable`: derivable de `table_type`, pero puede mantenerse como regla futura si hace falta granularidad
- `setup_cost`: coste base de mover o preparar la mesa
- `storage_resource_id`: opcional si una mesa de almacen se gestiona tambien como inventario agregado

Regla inicial: las mesas `STORAGE` no aparecen como mesas normales del salon ni como candidatas del algoritmo basico.

### StorageResource

Inventario agregado del restaurante.

Campos:

- `id`
- `restaurant_id`
- `resource_type`: `EXTRA_TABLE`, `EXTRA_CHAIR`, `HIGH_CHAIR`, `FOLDING_TABLE`, `TABLE_EXTENSION`, `BENCH`, `STORAGE_TABLE`, `OTHER`
- `name`
- `quantity`
- `capacity_per_unit`
- `setup_time_minutes`
- `active`
- `notes`
- `created_at`
- `updated_at`

Uso: registrar recursos como sillas extra, mesas plegables, tronas, extensiones o bancos. `STORAGE_TABLE` permanece como tipo compatible con V14. En Sprint 1 solo se configura y consulta el inventario; capacidad y tiempo de preparacion son descriptivos y no se aplican automaticamente al algoritmo.

### FloorPlanTemplate

Plantilla de plano base o alternativo.

Campos:

- `id`
- `restaurant_id`
- `name`
- `description`
- `active`
- `default_template`
- `created_at`
- `updated_at`

Uso: permitir layouts por temporada, terraza, eventos o turnos.

### DailyFloorPlan

Plano operativo de un dia.

Campos:

- `id`
- `restaurant_id`
- `business_date`
- `service_period`
- `floor_plan_template_id`
- `status`: `DRAFT`, `PUBLISHED`, `LOCKED`
- `created_by`
- `published_at`
- `created_at`
- `updated_at`

Uso: congelar decisiones diarias sin alterar el layout base.

### TableSetupOption

Opcion de montaje disponible o calculada.

Campos:

- `id`
- `restaurant_id`
- `name`
- `setup_type`: `SINGLE_TABLE`, `STANDARD_COMBINATION`, `EXTRA_CHAIRS`, `STORAGE_RESOURCE`, `SPECIAL_SETUP`
- `min_capacity`
- `max_capacity`
- `requires_manager_approval`
- `estimated_setup_minutes`
- `operational_cost`
- `active`
- `explanation_template`
- `created_at`
- `updated_at`

### TableSetupOptionItem

Recursos que componen una opcion.

Campos:

- `id`
- `table_setup_option_id`
- `resource_type`: `TABLE`, `TABLE_COMBINATION`, `STORAGE_RESOURCE`, `EXTRA_CHAIR`
- `resource_id`
- `quantity`
- `role`: `PRIMARY`, `EXTENSION`, `CHAIR`, `STORAGE`
- `order_index`

### ReservationSetupPlan

Plan propuesto, aprobado o aplicado para una reserva.

Campos:

- `id`
- `restaurant_id`
- `reservation_id`
- `table_setup_option_id`
- `status`: `PROPOSED`, `APPROVED`, `REJECTED`, `APPLIED`, `CANCELLED`
- `score`
- `operational_cost`
- `requires_manager_approval`
- `approved_by`
- `approved_at`
- `explanation_json`
- `created_at`
- `updated_at`

### SetupTask

Trabajo operativo que debe hacer el staff.

Campos:

- `id`
- `restaurant_id`
- `reservation_setup_plan_id`
- `reservation_id`
- `task_type`: `MOVE_TABLE`, `ADD_CHAIRS`, `FETCH_STORAGE_TABLE`, `OPEN_ROOM`, `PREPARE_SPECIAL_SETUP`, `MANAGER_APPROVAL`
- `title`
- `description`
- `status`: `PENDING`, `IN_PROGRESS`, `DONE`, `CANCELLED`
- `assigned_to`
- `due_at`
- `completed_by`
- `completed_at`
- `created_at`
- `updated_at`

## 5. Evolucion del algoritmo

### Nivel 1: mesa individual

Usar solo mesas activas de salon, no `STORAGE`, respetando capacidad, solapes, accesibilidad y prioridad de salon.

### Nivel 2: combinacion estandar

Usar combinaciones configuradas de mesas activas en salon. Debe seguir siendo determinista y explicable.

### Nivel 3: combinacion con sillas extra

Permitir superar capacidad normal dentro de un margen seguro si hay sillas extra suficientes y la mesa/salon lo permite.

### Nivel 4: mesa del almacen

Considerar recursos `STORAGE_TABLE` o mesas `STORAGE` solo como opcion avanzada. Debe generar coste operativo y tarea de preparacion.

### Nivel 5: montaje especial con aprobacion

Crear `ReservationSetupPlan` en estado `PROPOSED` cuando la solucion requiere mover varias mesas, abrir salon secundario, sacar almacen o preparar montaje no habitual. No se aplica automaticamente sin aprobacion.

### Nivel 6: sugerir hora alternativa para nueva solicitud

Solo para solicitudes nuevas, si no hay solucion viable a la hora pedida. Nunca debe modificar reservas existentes automaticamente.

## 6. Scoring propuesto

El score debe combinar calidad de capacidad, impacto futuro y coste operativo:

- `capacity_fit`: premia que la capacidad encaje con el grupo
- `wasted_seats_penalty`: penaliza sillas desperdiciadas
- `room_priority`: premia salones principales o preferidos
- `large_table_block_penalty`: penaliza bloquear mesas grandes para grupos pequenos
- `dead_gap_penalty`: penaliza huecos muertos antes o despues
- `move_table_cost`: penaliza mover mesas fisicas
- `storage_usage_cost`: penaliza sacar recursos de almacen
- `setup_time_cost`: penaliza preparaciones largas cerca de la hora de llegada
- `manager_approval_cost`: penaliza opciones que requieren aprobacion
- `future_reservation_impact`: penaliza reducir flexibilidad para reservas futuras

La explicacion debe separar restricciones duras, costes operativos y motivos comerciales.

## 7. Reglas de seguridad

- No cambiar horas de reservas existentes automaticamente.
- No usar recursos de almacen sin confirmacion o plan aprobado.
- No crear montajes imposibles por capacidad, espacio, accesibilidad o inventario.
- No exceder cantidad de sillas o mesas extra disponibles.
- Respetar accesibilidad declarada en salones y reservas.
- Respetar `restaurant_id` en todas las entidades, queries, eventos y permisos.
- Mantener trazabilidad de aprobaciones, rechazos y tareas completadas.
- El frontend puede sugerir acciones, pero la validacion vive en backend.

## 8. UX propuesta

### Configurar mesas de almacen

En configuracion de mesas, permitir tipo `STORAGE`. Estas mesas se muestran como inventario, no como mesas colocadas en un salon. La UI debe advertir que no aparecen como disponibles en el planning diario hasta que se apruebe un montaje.

### Configurar sillas extra

Crear una seccion de inventario de almacen con recursos tipo `EXTRA_CHAIR`. Debe mostrar cantidad total, estado activo e instrucciones internas.

### Crear combinaciones visuales

En una fase posterior, el editor debe permitir seleccionar mesas del plano y guardarlas como combinacion, indicando si requiere mover mesas o solo juntarlas.

### Mostrar montajes especiales en el planning

El planning debe diferenciar:

- asignacion normal
- combinacion estandar
- opcion con sillas extra
- montaje pendiente de aprobacion
- montaje aprobado con tareas pendientes

### Avisar al staff

Las `SetupTask` deben aparecer en panel operativo, notificaciones internas y detalle de reserva. Deben tener estado claro y responsable opcional.

### Confirmar o rechazar una opcion avanzada

Cuando una opcion requiere aprobacion, el manager ve coste, recursos, tareas y explicacion. Puede aprobar, rechazar o elegir una opcion mas simple.

## 9. Fases de implementacion

### Fase 1: tipos de mesa e inventario de almacen

Anadir `tableType` a mesas, crear `StorageResource`, API CRUD minima y UI de configuracion. El algoritmo solo debe excluir `STORAGE` de candidatos normales.

### Fase 2: combinaciones avanzadas

Extender combinaciones con coste, tipo, restricciones y uso limitado de sillas extra.

### Fase 3: setup options

Modelar opciones reutilizables y sus items. Permitir configurarlas y listarlas.

### Fase 4: algoritmo por niveles

Evaluar progresivamente niveles 1 a 5, manteniendo determinismo y explicabilidad. Nivel 6 solo para nuevas solicitudes.

### Fase 5: setup tasks

Generar tareas operativas desde planes aprobados.

### Fase 6: UI en planning

Mostrar planes, costes, aprobaciones y tareas dentro del planning diario.

### Fase 7: editor visual avanzado

Editor visual para combinaciones, montajes y floor plans diarios. No requiere 3D en MVP.

## 10. MVP recomendado

Implementar primero Fase 1:

- `tableType` en `RestaurantTable`
- `StorageResource` con cantidad y validacion de disponibilidad
- UI basica para ver mesas `STORAGE` y recursos como sillas extra
- exclusion de mesas `STORAGE` del planning y candidatos normales
- documentacion actualizada

Esto aporta valor rapido porque el restaurante puede empezar a registrar recursos reales sin cambiar todavia el comportamiento critico del algoritmo. Tambien prepara el modelo para fases avanzadas sin arriesgar reservas existentes.

## 11. Riesgos tecnicos

- Complejidad del algoritmo si se mezclan demasiados niveles a la vez.
- Datos mal configurados por el restaurante: capacidades irreales, recursos duplicados o cantidades incorrectas.
- Montajes fisicamente imposibles si no se modelan dimensiones, accesos y restricciones del salon.
- UI demasiado compleja para operacion rapida en tablet.
- Sobreoptimizacion antes de validar necesidades reales.
- Riesgo de romper asignacion actual si recursos de almacen entran como mesas normales.
- Falta de trazabilidad si se aplican montajes sin plan, aprobacion o tarea.

## Primera implementacion segura propuesta

La primera implementacion segura es Fase 1, limitada a modelo, API, UI minima y documentacion:

- anadir `tableType` con valores `FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`
- crear `StorageResource` con `resourceType`, `name`, `quantity`, `active` y `notes`
- permitir crear mesas `STORAGE`, pero excluirlas del planning y del algoritmo normal
- exponer API de inventario de almacen por restaurante
- anadir una comprobacion de disponibilidad de cantidad para preparar fases futuras
- mostrar en configuracion que existen recursos extra y mesas almacenadas

No se implementan todavia montajes especiales automaticos, aprobaciones, tareas, cambios de hora, IA, WhatsApp, 3D ni optimizacion profunda.
