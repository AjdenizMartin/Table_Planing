# Planning Panel Implementation Plan

## Fase 1: Planning funcional y estable

Objetivo: convertir `PlanningPage` en una pantalla principal premium, tactil y estable sin drag-and-drop ni cambios de hora.

Tareas:

- Crear header operativo con fecha, turno, salon, ocupacion, reservas, comensales y realtime.
- Crear panel izquierdo con reservas del dia, busqueda, filtros y reservas sin asignar.
- Crear plano visual 2D premium por salon.
- Crear estados visuales para mesas y reservas.
- Crear panel derecho de detalle de mesa/reserva.
- Crear timeline inferior de solo lectura.
- Mantener acciones existentes de asignacion automatica y movimiento manual por selector si ya son seguras.
- Mostrar claramente que la hora no cambia desde el planning.
- Manejar loading/error/empty states.
- Validar `npm run build`.

Criterios de finalizacion:

- Usuario demo entra y abre planning.
- Se ven salones, mesas y reservas.
- Se diferencian confirmadas, pendientes, sentadas, completadas y sin asignar.
- La pantalla es usable en tablet.
- No existe interaccion que cambie horas por accidente.

## Fase 2: Editor visual de salones y mesas tactil

Objetivo: separar modo servicio de modo edicion y permitir configurar el plano desde tablet.

Estado actual:

- Movimiento directo de mesas implementado con Pointer Events nativos.
- Guardado automatico al soltar mediante `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout`.
- Reversion local si backend rechaza el guardado.
- Snap-to-grid de 10px disponible en el editor.

Tareas:

- Crear `features/floor-plan`.
- Extraer el editor actual a componentes dedicados si crece la complejidad.
- Ampliar modo edicion con multi-select y alineacion.
- Permitir crear, duplicar, desactivar y editar mesas.
- Permitir cambiar forma, capacidad y dimensiones.
- Preparar undo/redo.
- Mantener guardado de layout con endpoint existente de mesa.
- Evaluar `dnd-kit` frente a `react-konva`.

Criterios:

- El manager puede configurar un salon sin tocar codigo.
- El modo edicion no afecta reservas activas.

## Fase 3: Drag and drop de reservas entre mesas manteniendo la hora original

Objetivo: reasignar reservas visualmente sin permitir cambios de hora.

Tareas:

- Introducir `dnd-kit`.
- Arrastrar reservas solo sobre mesas o combinaciones.
- Bloquear eje temporal y timeline drag.
- Crear prevalidacion visual.
- Backend valida capacidad, solapamiento, buffer, accesibilidad, tenant y hora inmutable.
- Mostrar razon de rechazo.
- Confirmar movimientos importantes.

Criterios:

- Una reserva puede moverse de mesa sin cambiar hora.
- No se puede arrastrar una reserva a otra hora.
- Todo movimiento pasa por backend.

## Fase 4: Vista 2.5D/isometrica premium

Objetivo: elevar impacto visual manteniendo estabilidad.

Tareas:

- Aplicar perspectiva/isometria ligera a mesas y zonas.
- Mejorar sombras, profundidad y etiquetas.
- Crear modo presentacion/demo.
- Mantener hit targets tactiles grandes.
- Evitar 3D real salvo prueba aislada.

Criterios:

- La vista impresiona en demo sin perder usabilidad.
- Sigue funcionando bien en tablet.

## Fase 5: Modo servicio en vivo

Objetivo: pantalla rapida para camareros y manager durante el servicio.

Tareas:

- Crear `features/live-service`.
- Mostrar mesas ocupadas ahora.
- Mostrar llegadas en 15/30/60 minutos.
- Mostrar retrasos, pendientes y limpieza.
- Acciones rapidas: arrived, seated, finished, no-show, reminder.
- Integrar WebSocket.

Criterios:

- El equipo puede operar el turno desde tablet con pocos toques.

## Fase 6: Optimizacion visual y sugerencias sin cambios de hora

Objetivo: mostrar oportunidades accionables sin tocar horarios.

Tareas:

- Crear `features/optimization`.
- Mostrar sugerencias de cambio de mesa, uso de salones y combinaciones.
- Mostrar antes/despues de capacidad.
- Prohibir recomendaciones de cambio horario automatico.
- Manager acepta o descarta.

Criterios:

- Cada sugerencia explica impacto y respeta hora original.

## Fase 7: Pulido UI/UX tablet

Objetivo: convertir el panel en experiencia premium.

Tareas:

- Refinar paleta, estados, animaciones y responsive.
- Mejorar accesibilidad tactil.
- Crear modo pantalla grande.
- Crear modo camarero simplificado.
- Crear datos demo realistas.
- Pruebas manuales en tablet.

Criterios:

- Demo fluida, clara y visualmente memorable.
- Sin errores de consola graves.
