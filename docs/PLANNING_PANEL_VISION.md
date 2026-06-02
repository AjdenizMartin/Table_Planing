# Planning Panel Vision

## 1. Objetivo del panel

El panel de planning debe ser la pantalla principal del producto: una cabina de mando tactil para que el restaurante vea salones, mesas, reservas, disponibilidad, conflictos y oportunidades de mejora durante el servicio.

La experiencia debe comunicar que Restaurant Table Planning no es una agenda de reservas, sino una herramienta inteligente para organizar el servicio como un puzzle operativo.

## 2. Problemas que resuelve

- Evita asignaciones simples que desperdician mesas grandes.
- Hace visibles reservas pendientes, sin asignar y con riesgo operativo.
- Permite entender la ocupacion por salon y por franja horaria.
- Reduce decisiones manuales repetitivas durante el servicio.
- Da al manager una vista tactil clara para operar desde tablet.
- Prepara futuras sugerencias de optimizacion sin delegar decisiones criticas en IA.

## 3. Regla critica: cambiar mesa, no hora

La aplicacion nunca debe cambiar automaticamente la hora de una reserva existente.

Permitido:

- Mover una reserva entre mesas manteniendo exactamente `reservation_date`, `start_time` y `end_time`.
- Mover una reserva entre salones si se conserva la hora original.
- Reasignar una reserva a una combinacion valida de mesas.
- Optimizar asignaciones de mesas sin tocar horarios.

No permitido:

- Arrastrar una reserva a otra hora.
- Optimizar desplazando reservas.
- Recomendar que un cliente cambie de hora como accion automatica.
- Cambiar hora fuera del formulario explicito de edicion de reserva.

El frontend debe mostrar esta regla de forma clara, pero la validacion definitiva debe vivir en backend.

## 4. Vistas necesarias

- Vista principal de plano visual por salon.
- Vista lista de reservas del dia con busqueda y filtros.
- Vista timeline por mesa y hora de solo lectura para horarios.
- Vista de reservas sin asignar.
- Vista de detalle de mesa o reserva seleccionada.
- Vista futura de editor de layout.
- Vista futura de servicio en vivo.
- Vista futura de sugerencias de optimizacion.

## 5. UX propuesta

La pantalla se organiza en cuatro zonas:

- Header superior: fecha, turno, salon, ocupacion, reservas, comensales, pendientes, acciones principales y estado realtime.
- Panel izquierdo: reservas del dia, filtros, busqueda, pendientes y sin asignar.
- Panel central: plano tactil del salon con mesas, estados, reservas y alertas.
- Panel derecho: detalle contextual de mesa o reserva con acciones rapidas.
- Timeline inferior: lectura por hora, reservas, buffers y huecos, sin drag horizontal.

Principios UX:

- Acciones grandes y claras para tablet.
- Estados con color, etiqueta e icono textual.
- Seleccion inmediata de mesa o reserva.
- Informacion importante siempre visible.
- Microinteracciones suaves, sin saturar.
- Separacion clara entre modo servicio y modo edicion.

## 6. Comparacion entre 2D, 2.5D y 3D

| Opcion | Impacto visual | Tablet | Complejidad | Mantenibilidad | Recomendacion |
| --- | --- | --- | --- | --- | --- |
| 2D premium HTML/SVG | Alto | Excelente | Baja-media | Alta | Ideal para Fase 1 |
| 2.5D isometrico CSS/SVG | Muy alto | Muy buena | Media | Buena | Ideal para MVP avanzado |
| Canvas/Konva/Fabric | Alto | Buena | Media-alta | Media | Valioso para editor tactil avanzado |
| React Flow | Medio | Media | Media | Buena | Mejor para grafos, no para floor plan |
| Three.js/R3F | Muy alto | Variable | Alta | Media-baja | Futuro showroom, no MVP operativo |

## 7. Recomendacion tecnica final

Construir primero un plano 2D premium con lenguaje visual preparado para evolucionar a 2.5D. Usar HTML/CSS/SVG nativo para Fase 1 y mantener los datos en modelos TypeScript claros.

No introducir Three.js ni React Three Fiber todavia. El valor de demo se consigue mejor con una interfaz estable, tactil y bonita que con una escena 3D dificil de operar.

## 8. Librerias recomendadas y por que

- Fase 1: sin librerias nuevas. React, TypeScript, Tailwind y TanStack Query son suficientes.
- Fase 2/3: `dnd-kit` para mover mesas y reservas entre mesas, porque funciona bien con React y permite restricciones de eje/zonas.
- Fase 2/3 opcional: `react-konva` si el editor necesita multi-select, snap, rotacion, paredes y objetos decorativos complejos.
- Timeline: CSS Grid propio. FullCalendar es potente, pero tiende a imponer semantica de calendario con drag temporal, justo lo que queremos evitar.
- 3D futuro: React Three Fiber solo para modo presentacion, no para operativa diaria.

## 9. Estructura frontend propuesta

```text
frontend/src/features/planning/
  api/
  components/
    PlanningHeroHeader.tsx
    ReservationQueue.tsx
    FloorPlanCanvas.tsx
    TableCard.tsx
    ReservationDetailPanel.tsx
    ReadOnlyTimeline.tsx
    PlanningStatusLegend.tsx
  pages/
    PlanningPage.tsx
  types.ts
  utils/
    planningStatus.ts
```

Futuro:

```text
features/floor-plan/
features/live-service/
features/optimization/
```

## 10. Endpoints backend necesarios

Ya existen:

- `GET /api/restaurants/{restaurantId}/planning?date=YYYY-MM-DD`
- `POST /api/restaurants/{restaurantId}/planning/recalculate`
- `POST /api/restaurants/{restaurantId}/planning/move-reservation`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assign`

Necesarios para fases futuras:

- `POST /api/restaurants/{restaurantId}/planning/validate-table-move`
- `POST /api/restaurants/{restaurantId}/planning/simulate`
- `GET /api/restaurants/{restaurantId}/planning/live`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout`
- `POST /api/restaurants/{restaurantId}/tables/{tableId}/block`
- `POST /api/restaurants/{restaurantId}/tables/{tableId}/unblock`
- `GET /api/restaurants/{restaurantId}/optimization/suggestions?date=YYYY-MM-DD`

No se deben crear endpoints duplicados si los existentes cubren el caso.

## 11. Modelo de datos necesario

Necesario actual:

- `DiningRoom`
- `RestaurantTable`
- `Reservation`
- `ReservationAssignment`
- `PlanningDayResponse`
- `PlanningConflict`

Futuro:

- `TableBlock`
- `TableStatusEvent`
- `LayoutObject`
- `OptimizationSuggestion`
- `PlanningSimulation`
- `WaitlistEntry`

## 12. Fases de implementacion

1. Planning funcional y estable.
2. Editor visual de salones y mesas tactil.
3. Drag and drop de reservas entre mesas manteniendo hora.
4. Vista 2.5D/isometrica premium.
5. Modo servicio en vivo.
6. Optimizacion visual y sugerencias sin cambios de hora.
7. Pulido tablet y modos de uso.

## 13. Que incluir en MVP

- Login demo.
- Planning diario con fecha y salon.
- Plano visual por salon.
- Lista de reservas y reservas sin asignar.
- Estados visuales claros.
- Panel de detalle.
- Timeline de solo lectura.
- Accion de asignacion automatica existente.
- Mensajes de error claros.

## 14. Que dejar para futuras fases

- Drag and drop.
- Editor avanzado de paredes, objetos y rotacion.
- 2.5D completo.
- Modo live service profundo.
- Simulaciones what-if.
- Comparacion antes/despues.
- WhatsApp.
- IA generativa.
- Prediccion de ocupacion.

## 15. Riesgos tecnicos

- Planning backend lento o bloqueante si las queries cargan demasiadas relaciones.
- Drag and drop accidental que cambie hora si se usa un calendario generico.
- Exceso visual que reduzca claridad en tablet.
- Estado duplicado entre plano, timeline y panel lateral.
- Reglas de negocio en frontend que no coincidan con backend.
- Reasignaciones sin validacion tenant/rol.

Mitigacion:

- Backend como fuente de verdad.
- Timeline read-only.
- Mutaciones solo por endpoints de reasignacion.
- Tipos compartidos claros.
- Componentes pequenos y testeables.
- No introducir librerias pesadas antes de necesitarlas.

## 16. Como validarlo en demo

- Entrar con `demo@restaurant.com`.
- Abrir `Planning`.
- Cambiar fecha y salon.
- Ver 3 salones y mesas demo.
- Seleccionar mesa y reserva.
- Ver pendientes y sin asignar.
- Mostrar que la timeline no permite cambiar horarios.
- Explicar que la optimizacion solo mueve mesas.
- Mostrar estados, ocupacion y comensales.
