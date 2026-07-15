# Algorithm

## Objetivo

El motor de asignacion debe decidir la mejor mesa o combinacion para cada reserva, no solo una mesa libre cualquiera. El objetivo es optimizar ocupacion, flexibilidad futura y calidad operativa del servicio.

## Version implementada actualmente

Estado actual: `PILOT_READY`

Implementado hoy:

- ventana efectiva usando `start_time`, `end_time` y `cleaning_buffer`
- candidatos con mesas activas y combinaciones predefinidas activas
- restricciones duras de capacidad, actividad, accesibilidad y solapamiento
- scoring determinista con explicacion persistida
- desempate determinista
- exclusion de mesas `STORAGE` como candidatas normales
- exclusion defensiva de combinaciones que contengan mesas `STORAGE`
- modos separados `AUTOMATIC` y `MANUAL_SUGGESTION`
- top 3 manual con combinaciones avanzadas e inventario
- capacidad adicional por recursos y disponibilidad temporal
- penalizaciones operativas avanzadas deterministas
- aplicacion transaccional con bloqueo y revalidacion

No implementado todavia:

- replanning profundo o cascadas de reasignacion
- optimizacion batch por turno completo
- simulacion avanzada de demanda futura
- disponibilidad como modulo/API separado
- montajes especiales con aprobacion y tareas operativas

## Principios

- algoritmo determinista antes que heuristicas opacas
- explicabilidad obligatoria
- respeto estricto de restricciones duras
- scoring configurable y mejorable
- IA solo como apoyo explicativo

## Problema a resolver

Cuando entra una nueva reserva, el sistema debe decidir:

- si puede aceptarse o no
- en que mesa o combinacion debe asignarse
- si conviene reservar un recurso mas flexible
- si existe recolocacion limitada que mejore el planning

## Entradas del algoritmo

- fecha
- hora solicitada
- numero de comensales
- duracion estimada
- tiempo de limpieza
- reglas del restaurante
- salones habilitados
- mesas disponibles
- combinaciones permitidas
- reservas existentes
- preferencias del cliente
- requisitos de accesibilidad

## Restricciones duras

Estas restricciones deben cumplirse siempre:

- la capacidad debe soportar el tamano del grupo
- no puede haber solapamiento horario efectivo
- debe respetarse el buffer de limpieza
- el salon debe ser permitido por reglas
- accesibilidad debe respetarse si aplica
- la mesa o combinacion debe estar activa

## Ventana efectiva de ocupacion

La reserva no ocupa solo la hora exacta del cliente. Se debe calcular:

- inicio real
- fin real

Formula conceptual:

```text
inicio_real = hora_reserva
fin_real = hora_reserva + duracion_estimada + buffer_limpieza
```

Para un candidato avanzado el inicio efectivo se adelanta por su preparacion:

```text
inicio_inventario = hora_reserva - setup_time_minutes
fin_inventario = fin_reserva + buffer_limpieza
```

## Estrategia de seleccion

### Paso 1. Normalizar solicitud

- resolver duracion segun tamaño del grupo y reglas
- aplicar limpieza y margenes
- cargar contexto del restaurante

### Paso 2. Buscar candidatos

Modo `AUTOMATIC` busca mesas individuales y combinaciones `STANDARD`. Modo `MANUAL_SUGGESTION` anade combinaciones `ADVANCED`, calcula recursos requeridos y limita la salida ordenada a tres opciones.

En una fase avanzada se podran generar combinaciones dinamicas controladas.

Desde Fase 1 de planificacion avanzada, las mesas con `table_type = STORAGE` quedan fuera de la busqueda normal. Las combinaciones estandar tampoco deben contener mesas `STORAGE`; el backend lo valida al crear o actualizar combinaciones y el finder las ignora defensivamente si aparecen por datos legacy. Solo deben evaluarse en niveles avanzados con aprobacion o plan de montaje.

`StorageResource` solo entra mediante requisitos de una combinacion avanzada. Todos sus tipos son validos. Cada unidad aporta `capacity_per_unit`; valor cero permite modelar manteleria, extensiones u otros recursos sin plazas. La disponibilidad resta cantidades consumidas por asignaciones activas cuyas ventanas se solapan.

La seleccion manual bloquea en base de datos los recursos requeridos en orden de id y repite la comprobacion antes de persistir. Dos transacciones concurrentes no pueden superar `StorageResource.quantity`.

## Evolucion avanzada por niveles

La evolucion prevista se documenta en [docs/ADVANCED_TABLE_PLANNING_DESIGN.md](./docs/ADVANCED_TABLE_PLANNING_DESIGN.md):

- Nivel 1: mesa individual de salon.
- Nivel 2: combinacion estandar.
- Nivel 3: combinacion con sillas extra.
- Nivel 4: mesa o recurso de almacen.
- Nivel 5: montaje especial con aprobacion del manager.
- Nivel 6: sugerir hora alternativa solo para una nueva solicitud.

Regla de seguridad: el algoritmo no debe cambiar la hora de reservas existentes. Cualquier cambio horario requiere solicitud del cliente y edicion manual del staff.

### Paso 3. Filtrar por restricciones duras

Eliminar cualquier opcion que:

- no tenga capacidad suficiente
- ya este ocupada
- viole reglas del salon
- rompa accesibilidad

### Paso 4. Evaluar impacto local y futuro

Para cada candidato:

- medir capacidad desperdiciada
- medir huecos muertos antes y despues
- medir bloqueo de mesas grandes
- medir perdida de flexibilidad futura
- medir uso de salones no prioritarios

### Paso 5. Calcular score

Cada opcion recibe una puntuacion total compuesta por bonuses y penalizaciones.

Las combinaciones avanzadas anaden:

```text
operational_cost_penalty = LOW 8 | MEDIUM 24 | HIGH 48
setup_time_penalty = min(setup_time_minutes * 0.5, 30)
```

### Paso 6. Elegir mejor opcion

Seleccionar por score descendente y desempate estable por tipo e id. En sugerencia manual se devuelven como maximo tres.

### Paso 7. Explicar decision

Guardar:

- candidato elegido
- alternativas principales
- factores de score
- reglas activadas

## Recolocacion limitada

Documentado como evolucion prevista. La version actual no hace recolocacion automatica profunda; solo soporta movimiento manual desde planning y asignacion automatica directa.

## Formula inicial de scoring

```text
score_total =
  w1 * capacity_fit
+ w2 * room_priority
+ w3 * future_flexibility
+ w4 * preference_match
+ w5 * accessibility_match
+ w6 * service_flow_alignment
- w7 * wasted_seats_penalty
- w8 * dead_gap_penalty
- w9 * large_table_block_penalty
- w10 * room_activation_penalty
- w11 * recombination_cost
- w12 * reassignment_cost
- w13 * fragmentation_penalty
## Significado de factores

### Bonuses

- `capacity_fit`: premia capacidad cercana al tamaño del grupo
- `room_priority`: premia el salon preferente
- `future_flexibility`: premia dejar opciones utiles para mas tarde
- `preference_match`: premia preferencias del cliente o manager
- `accessibility_match`: premia asignaciones aptas
- `service_flow_alignment`: premia equilibrio operativo

### Penalizaciones

- `wasted_seats_penalty`: castiga ocupar demasiada capacidad
- `dead_gap_penalty`: castiga dejar huecos inutilizables
- `large_table_block_penalty`: castiga usar mesas grandes para grupos pequeños
- `room_activation_penalty`: castiga abrir un salon secundario antes de tiempo
- `recombination_cost`: castiga combinaciones complejas innecesarias
- `reassignment_cost`: castiga mover reservas existentes
- `fragmentation_penalty`: castiga dispersar en exceso el servicio

## Ejemplos de comportamiento esperado

### Caso 1

Reserva de 2 personas:

- si hay mesa de 2 disponible, debe priorizarse frente a mesa de 6
- salvo que el analisis detecte que la mesa de 2 es estrategica para otra franja

### Caso 2

Reserva de 4 personas a las 20:30:

- si no hay hueco directo, se puede evaluar mover una reserva pequeña
- la recolocacion solo debe aceptarse si mejora claramente el conjunto

### Caso 3

Cliente con movilidad reducida:

- debe evitarse el salon con escalera
- aunque haya disponibilidad alli, la opcion debe descartarse o penalizarse fuertemente segun la regla

## Explicabilidad

Cada asignacion debe devolver un resumen similar a:

```text
Seleccionada mesa M12 del salon principal.
Motivos:
- capacidad ajustada al grupo de 4
- evita bloquear mesa de 6
- mantiene libre una combinacion util a las 21:00
- respeta prioridad del salon principal
```

## Parametrizacion

Los pesos del scoring deben ser configurables por defecto a nivel plataforma y ajustables mas adelante por restaurante, con cuidado para no romper consistencia.

## Estrategia de evolucion

### Version 1

- mesas individuales
- combinaciones predefinidas
- scoring heuristico
- explicacion persistida

### Version 2

- combinaciones dinamicas controladas
- simulacion de demanda futura
- ajuste de pesos con datos historicos

### Version 3

- optimizacion por turno completo
- simulaciones batch
- recomendaciones avanzadas apoyadas por IA

## Requisitos de testing del algoritmo

- escenarios felices
- escenarios limite
- conflictos temporales
- accesibilidad
- prioridad de salones
- huecos muertos
- recolocacion
- no regresion entre versiones
