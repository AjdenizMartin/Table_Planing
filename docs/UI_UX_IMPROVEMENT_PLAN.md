# UI/UX Improvement Plan

## 1. Problema actual

La aplicación tiene dos pantallas separadas para gestionar reservas (`/reservations` y `/planning`), cada una con capacidades complementarias pero desconectadas. El usuario debe navejar entre ellas constantemente, perdiendo contexto operativo.

### Síntomas principales

- 9 items en el nav principal + sub-navegación interna en varias pantallas.
- PlanningPage (1044 líneas) contiene toda la lógica inline sin separación de componentes.
- No hay acciones de estado (confirmar, cancelar, sentar) disponibles desde planning.
- El botón "New reservation" en planning navega a otra pantalla.
- El botón "Edit layout" en planning navega a otra pantalla.
- HomePage muestra datos de debug irrelevantes para operación diaria.
- No existe un componente reutilizable de panel lateral/overlay.
- La fecha seleccionada no se sincroniza entre planning y reservas.

---

## 2. Pantallas actuales

| # | Ruta | Componente | Propósito | Problema |
|---|------|-----------|-----------|----------|
| 1 | `/` | HomePage | Dashboard de bienvenida | Muestra información de debug, no útil para operación |
| 2 | `/planning` | PlanningPage | Plano visual de mesas | Sin acciones de estado, 1044 líneas inline |
| 3 | `/reservations` | ReservationsPage | CRUD de reservas | Sin contexto visual, duplicado lógico con planning |
| 4 | `/customers` | CustomersPage | Lista de clientes | Correcta, pero debería accesible desde planning |
| 5 | `/customers/:id` | CustomerDetailPage | Detalle de cliente | Correcta |
| 6 | `/notifications` | NotificationsPage | Lista de notificaciones | Accesible desde campana en header |
| 7 | `/settings/restaurant` | RestaurantSettingsPage | Config restaurante | 1 de 5 pantallas de settings |
| 8 | `/settings/dining-rooms` | DiningRoomsPage | Gestión salones | Separada innecesariamente |
| 9 | `/settings/tables` | TablesPage | Gestión mesas | Separada innecesariamente |
| 10 | `/settings/layout` | TableLayoutEditorPage | Editor de plano | Separada innecesariamente |
| 11 | `/settings/table-combinations` | TableCombinationsPage | Combinaciones de mesas | Separada innecesariamente |

---

## 3. Flujo actual

### 3.1 Ver una reserva

```
PlanningPage (pinchar reserva)
  → DetailPanel (columna derecha, 360px)
    → Muestra: nombre, hora, pax, estado, mesa, notas
    → NO muestra: canal, teléfono, confirmado-en, cancelado-en
    → Única acción disponible: "Find best table" (si no asignada)
```

Para ver todos los datos → hay que ir a `/reservations`, buscar la fecha, encontrar la reserva.

### 3.2 Confirmar / Cancelar una reserva

```
PlanningPage
  → Nav a /reservations?date=...
  → Encontrar reserva en lista
  → Pinchar reserva → se abre ReservationDetailPanel
  → Click "Confirmar" o "Cancelar"
  → Volver a /planning (pierde scroll, selección, contexto)
```

**3 navegaciones distintas para una acción simple.**

### 3.3 Editar una reserva

No existe flujo de edición. El endpoint `PATCH /{id}` existe, pero no hay UI. Para cambiar datos de una reserva, el usuario no tiene camino definido.

### 3.4 Crear una reserva

```
PlanningPage
  → Click "New reservation"
  → Navega a /reservations?mode=new&date=...
  → Rellena formulario (nombre, teléfono, hora, pax, etc.)
  → Click "Crear"
  → Vuelve a /planning (pierde contexto)
```

### 3.5 Reasignar mesa

```
PlanningPage
  → Pinchar reserva no asignada → DetailPanel
  → Click "Find best table" (asignación automática)
  → O arrastrar en FloorPlan (no implementado aún)
```

---

## 4. Flujo recomendado (Planning First)

### Principio

El usuario nunca abandona `/planning` para tareas operativas del día.

#### 4.1 Ver una reserva

```
PlanningPage
  → Pinchar reserva en FloorPlan, Queue o Timeline
  → ReservationSidePanel (overlay derecho, ~576px)
    → Muestra: TODO (nombre, teléfono, pax, fecha, hora, duración,
      mesa, salón, estado, confirmación, canal, notas, accesibilidad)
    → Acciones disponibles según estado
```

#### 4.2 Confirmar / Cancelar / Sentar / Completar

```
PlanningPage → ReservationSidePanel
  → Click "Confirmar" → llama a POST /confirm
  → Panel se mantiene abierto con datos actualizados
  → Planning se refresca en background
  → Toast: "Reservation confirmed."
```

**0 navegaciones. 1 click.**

#### 4.3 Editar reserva

```
PlanningPage → ReservationSidePanel
  → Click "Edit details"
  → EditReservationModal (modal centrado)
  → Editar party size, notas, accesibilidad
  → SIN campo de hora (protegido)
  → Click "Guardar" → PATCH /{id}
```

#### 4.4 Crear reserva

```
PlanningPage
  → Click "New reservation" en HeroHeader
  → CreateReservationPanel (overlay o modal, mismo patrón)
  → Rellenar formulario mínimo (cliente, hora, pax)
  → Click "Crear" → POST /reservations + auto-assign
  → Planning se refresca
```

#### 4.5 Reasignar mesa

```
PlanningPage → ReservationSidePanel
  → Click "Reassign table"
  → POST /assign (existente)
  → Panel se actualiza con nueva mesa
  → FloorPlan se refresca
```

---

## 5. Componentes nuevos necesarios

| Componente | Propósito | Basado en |
|---|---|---|
| `ReservationSidePanel` | Overlay lateral con detalle completo + acciones rápidas | Patrón de `InsightPanel` |
| `EditReservationModal` | Modal para editar datos básicos (sin hora) | Nuevo |
| `PanelPrimitive` | Reusable slide-over/drawer con backdrop (futuro) | Extraer de `InsightPanel` + `ReservationSidePanel` |

## 6. Componentes existentes reutilizables

| Componente | Dónde está | Se reutiliza para |
|---|---|---|
| `ReservationDetailPanel` (acciones) | `frontdesk/components/` | Lógica de confirm/cancel/seat/complete/no-show |
| `StatusPill` | `frontdesk/components/` | Mostrar estado en side panel |
| `StatusMessage` | `restaurant-config/components/` | Mensajes de error/success |
| `ConfigCard` | `restaurant-config/components/` | Secciones de información |
| `Field` (TextField, SelectField...) | `restaurant-config/components/` | Formularios |
| `getErrorMessage` | `restaurant-config/utils/` | Parseo de errores API |
| `notify()` | `notifications/components/` | Toast de éxito |
| `formatReservationCustomerName` | `frontdesk/utils/` | Nombre completo |
| `formatReservationStatus` | `frontdesk/utils/` | Label de estado en español |
| `normalizeTimeForInput` | `frontdesk/utils/` | Formato HH:mm |

## 7. Pantallas que se deben simplificar

| Pantalla | Problema | Propuesta |
|---|---|---|
| **HomePage (`/`)** | Muestra debug session, no útil | Redirigir a `/planning` |
| **AppLayout nav** | 9 items, demasiados | Reducir a: Planning, Clientes, Notificaciones, Config (settings agrupados) |
| **ReservationsPage** | Duplica funcionalidad de planning | Convertir en vista auxiliar de búsqueda/archivo histórico |
| **Settings (5 páginas)** | Demasiada fragmentación | Agrupar en 1-2 pantallas con tabs o secciones |
| **PlanningPage** | 1044 líneas inline | Extraer componentes a archivos separados |
| **OperationsShell** | Sub-nav redundante (Clientes/Reservas) | Eliminar una vez que reservations se integre en planning |
| **ConfigShell** | Sub-nav para settings | Mantener pero simplificar |

## 8. Acciones rápidas necesarias

### Desde ReservationSidePanel:

| Acción | Endpoint | Estado |
|---|---|---|---|
| Confirm reservation | `POST /{id}/confirm` | ✅ Existe |
| Cancel reservation | `POST /{id}/cancel` | ✅ Existe |
| Mark arrived | `POST /{id}/arrived` | ✅ Existe |
| Mark seated | `POST /{id}/seat` | ✅ Existe |
| Mark finished | `POST /{id}/complete` | ✅ Existe |
| Mark no-show | `POST /{id}/no-show` | ✅ Existe |
| Send reminder | `POST /{id}/notifications/reminder` | ✅ Existe |
| Send confirmation SMS | `POST /{id}/notifications/confirmation` | ✅ Existe |
| Reassign table | `POST /{id}/assign` | ✅ Existe |
| Open edit modal | `PATCH /{id}` | ✅ Existe + implementado |

## 9. Riesgos técnicos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El panel lateral superpone al FloorPlan y puede sentirse disruptivo | Medio | Backdrop translúcido, animación suave, cierre fácil (click fuera, Escape) |
| Múltiples queries al abrir un panel (reservation detail + customer) | Bajo | Las queries son rápidas (GET por ID), se pueden cachear con TanStack Query |
| Inconsistencia entre planning summary y reservation detail | Medio | Usar GET /reservations/{id} como fuente de verdad para el panel, planning summary solo para display visual |
| Mutaciones desde planning no invalidan todos los caches | Alto | Invalidar siempre `["planning", ...]`, `["reservations", ...]`, y `["aiInsights", ...]` |
| Type mismatch entre `PlanningReservationSummaryResponse` y `ReservationResponse` | Medio | Convertir/mapear en el panel; no mezclar tipos |
| Mobile: panel lateral no funciona en vertical | Bajo | Usar modal centrado en <1024px en lugar de side panel |
| El botón "Edit details" podría permitir cambios de hora si no se protege | Alto | El `EditReservationModal` NO incluye campos de fecha/hora por diseño. La validación se refuerza en backend |

## 10. Plan por fases

### Fase 1 (este sprint) — Acciones rápidas en planning

**Objetivo:** Poder ver detalle completo y ejecutar acciones de estado desde planning.

| Tarea | Archivos |
|---|---|
| Crear `ReservationSidePanel` | `features/planning/components/ReservationSidePanel.tsx` |
| Añadir `sendReservationConfirmation()` a API | `features/frontdesk/api/frontdeskApi.ts` |
| Integrar panel en PlanningPage | `features/planning/pages/PlanningPage.tsx` |
| Crear `EditReservationModal` | `features/planning/components/EditReservationModal.tsx` |
| Documentar | `docs/UI_UX_IMPROVEMENT_PLAN.md` |

**Criterios:** Ver detalle completo, confirmar, cancelar, sentar, completar, no-show, reasignar, recordatorio SMS, y editar datos básicos desde planning.

---

### Fase 2 — Panel de creación en planning

**Objetivo:** Crear nuevas reservas sin salir de planning.

| Tarea | Archivos |
|---|---|
| Crear `CreateReservationPanel` (modal/overlay) | `features/planning/components/CreateReservationPanel.tsx` |
| Reutilizar lógica de `ReservationForm` | Extraer validación + submit a hook compartido |
| Botón "New reservation" en HeroHeader → abre panel | `PlanningPage.tsx` |
| Auto-assign después de crear | Flujo existente |

**Criterios:** Crear reserva, asignar mesa automáticamente, ver resultado en FloorPlan. Sin navegación.

---

### Fase 3 — Simplificación de navegación

**Objetivo:** Reducir fricción de navegación.

| Tarea | Archivos |
|---|---|
| HomePage redirige a `/planning` | `features/home/HomePage.tsx` → `router.tsx` |
| Reducir AppLayout nav a 4-5 items | `components/layout/AppLayout.tsx` |
| Agrupar settings en tabs (1-2 páginas) | `features/restaurant-config/pages/*` |
| Extraer componentes inline de PlanningPage | `features/planning/components/*` |

**Criterios:** Nav limpio, planning es landing, planning tiene componentes extraídos.

---

### Fase 4 — Extraer PanelPrimitive

**Objetivo:** Tener un componente reutilizable de side panel para toda la app.

| Tarea | Archivos |
|---|---|
| Extraer `SlideOver` de `InsightPanel` y `ReservationSidePanel` | `src/components/ui/SlideOver.tsx` |
| Refactorizar `InsightPanel` para usarlo | `features/ai/components/InsightPanel.tsx` |
| Refactorizar `ReservationSidePanel` para usarlo | `features/planning/components/ReservationSidePanel.tsx` |

**Criterios:** SidePanel reutilizable con animación, backdrop, cierre por Escape/click fuera, responsive.

---

### Fase 5 — Sincronización de estado global

**Objetivo:** La fecha seleccionada y la reserva activa persisten entre vistas.

| Tarea | Archivos |
|---|---|
| Mover `selectedDate` a contexto o URL search params | Context o router |
| Sincronizar planning y reservations | Ambos consumen mismo origen |
| Persistir `selectedReservationId` en URL | `planning?date=...&reservationId=...` |

**Criterios:** Cambiar fecha en planning → misma fecha en reservas. Pinchar reserva → URL refleja selección.

---

## 11. Criterios de aceptación generales

- [x] Puedo ver detalle completo de una reserva desde el planning
- [x] Puedo confirmar una reserva desde el planning
- [x] Puedo cancelar una reserva desde el planning
- [x] Puedo marcar seated/finished/no-show desde el planning
- [x] Puedo marcar arrived desde el planning
- [x] Puedo enviar recordatorio SMS desde el planning
- [x] Puedo editar datos básicos desde el planning
- [x] Puedo reasignar mesa desde el planning
- [x] El panel se abre/cierra con animación suave
- [x] El panel es responsive (lateral en desktop, modal en mobile)
- [x] Las acciones muestran loading/error/success
- [x] El planning se refresca después de cada acción
- [x] La hora de la reserva nunca cambia por accidente
- [x] `npm run build` funciona

---

## Apéndice A: Archivos revisados

### Frontend (59 archivos .ts/.tsx en 8 features)

```
src/
├── app/router.tsx                         (12 rutas, raíz de navegación)
├── components/layout/AppLayout.tsx         (9 items nav, header, restaurant selector)
├── features/
│   ├── ai/
│   │   ├── components/InsightBar.tsx
│   │   ├── components/InsightPanel.tsx     (slide-over reusable como patrón)
│   │   └── hooks/useAiInsights.ts
│   ├── auth/
│   │   ├── context/AuthContext.tsx
│   │   └── pages/LoginPage.tsx
│   ├── frontdesk/
│   │   ├── api/frontdeskApi.ts            (CRUD + status transitions)
│   │   ├── components/
│   │   │   ├── OperationsShell.tsx         (sub-nav redundante)
│   │   │   ├── ReservationDetailPanel.tsx  (acciones de estado completas)
│   │   │   ├── ReservationForm.tsx         (creación con validación)
│   │   │   └── StatusPill.tsx
│   │   ├── pages/
│   │   │   ├── CustomersPage.tsx
│   │   │   ├── CustomerDetailPage.tsx
│   │   │   └── ReservationsPage.tsx       (341 líneas, duplicado lógico)
│   │   ├── utils/frontdeskUtils.ts
│   │   └── types.ts
│   ├── home/HomePage.tsx                  (debug, no operativo)
│   ├── notifications/
│   │   ├── components/NotificationToast.tsx (notify() global)
│   │   └── pages/NotificationsPage.tsx
│   ├── planning/
│   │   ├── api/planningApi.ts
│   │   ├── components/PlanningGrid.tsx     (dead code, no se importa)
│   │   ├── pages/PlanningPage.tsx          (1044 líneas, TODO inline)
│   │   └── types.ts
│   ├── realtime/
│   │   └── RealtimeProvider.tsx
│   └── restaurant-config/
│       ├── api/configApi.ts
│       ├── components/
│       │   ├── ConfigShell.tsx
│       │   ├── ConfigCard.tsx
│       │   ├── Field.tsx
│       │   └── StatusMessage.tsx
│       ├── hooks/useActiveRestaurant.ts
│       ├── pages/
│       │   ├── RestaurantSettingsPage.tsx
│       │   ├── DiningRoomsPage.tsx
│       │   ├── TablesPage.tsx
│       │   ├── TableLayoutEditorPage.tsx
│       │   └── TableCombinationsPage.tsx
│       ├── utils/errorMessage.ts
│       └── types.ts
└── services/api/client.ts                 (ApiClient + ApiError)
```

### Backend (relevante para endpoints)

```
backend/.../reservation/
├── api/ReservationController.java          (status endpoints)
├── api/ReservationResponse.java            (DTO completo)
├── domain/ReservationStatus.java           (enum: PENDING, CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW)
└── service/ReservationService.java         (ensureTransitionAllowed, confirm, cancel, seat, complete, noShow)

backend/.../notification/
└── api/ReservationSmsNotificationController.java  (POST /notifications/confirmation)

backend/.../planning/
└── api/PlanningController.java             (GET planning, POST recalculate, POST move-reservation)
```

---

## Apéndice B: Problemas detectados

### Críticos

1. **Dos sistemas paralelos**: PlanningPage (solo lectura visual) y ReservationsPage (solo acciones sin contexto). El usuario debe navegar entre ambos constantemente.
2. **1044 líneas en PlanningPage**: Todo el código está inline, sin separación de componentes, imposible de mantener a largo plazo.
3. **No hay acciones de estado en planning**: El DetailPanel dentro de planning solo muestra información y tiene un único botón "Find best table". Confirmar, cancelar, sentar, completar no existen.

### Altos

4. **HomePage inútil**: Muestra datos de debug de sesión. No aporta valor operativo.
5. **Navegación sobrecargada**: 9 items en AppLayout + sub-navegación en OperationsShell y ConfigShell = demasiadas opciones.
6. **Settings fragmentadas**: 5 pantallas separadas para configuración que podrían agruparse.
7. **Type mismatch**: Planning usa `PlanningReservationSummaryResponse` (con `reservationId`), frontdesk usa `ReservationResponse` (con `id`). No se pueden intercambiar componentes entre features sin mapeo.

### Medios

8. **No hay panel lateral reutilizable**: `InsightPanel` es un one-off. Cada nuevo overlay se implementa desde cero.
9. **OperacionesShell duplica navegación**: Tiene sidebar con "Clientes" y "Reservas" cuando AppLayout ya tiene esos links.
10. **PlanningGrid.tsx es dead code**: Definido pero no importado, ocupa espacio.
11. **La fecha no se sincroniza**: Cambiar fecha en planning no afecta a la fecha en reservas y viceversa.

### Bajos

12. **Faltan animaciones/transiciones**: Los cambios de estado y apertura de paneles son instantáneos, sin feedback visual.
13. **Falta responsive consistente**: Algunas pantallas tienen layout responsive, otras no.
14. **Falta soporte para teclado**: Algunos botones no tienen manejo de teclado.

---

## Apéndice C: Qué implementar primero

### Prioridad 1: ReservationSidePanel con acciones

Dar al planning las acciones de estado que hoy solo existen en frontdesk. Es el cambio de mayor impacto con menor riesgo.

**Archivos:**
- `frontend/src/features/planning/components/ReservationSidePanel.tsx` (nuevo)
- `frontend/src/features/planning/components/EditReservationModal.tsx` (nuevo)
- `frontend/src/features/planning/pages/PlanningPage.tsx` (modificar)
- `frontend/src/features/frontdesk/api/frontdeskApi.ts` (añadir sendConfirmation)

### Prioridad 2: Simplificar navegación

Reducir fricción antes de añadir más funcionalidad.

### Prioridad 3: Extraer componentes de PlanningPage

Dividir el monolito de 1044 líneas en archivos manejables.

### Prioridad 4: Panel de creación en planning

Completar el círculo: todo el ciclo de vida de la reserva desde planning.

---

## Apéndice D: Progreso

### Sprint 4: Acciones rápidas completas en ReservationSidePanel (completado)

**Objetivo:** Todas las acciones de estado disponibles desde el panel lateral de planning.

**Nuevo en backend:**
- ✅ `ReservationStatus.ARRIVED` añadido con transiciones: `PENDING/CONFIRMED → ARRIVED`, `ARRIVED → SEATED/CANCELLED/NO_SHOW`
- ✅ `ReservationService.arrived()` — endpoint `POST /{id}/arrived`
- ✅ `NotificationTemplateCode.RESERVATION_REMINDER` — nuevo template
- ✅ `SmsNotificationService.sendReservationReminder()` — endpoint `POST /{id}/notifications/reminder`
- ✅ Backend compila sin errores

**Nuevo en frontend:**
- ✅ `ReservationSidePanel.tsx` (~436 líneas) — overlay lateral con detalle completo + acciones
- ✅ `EditReservationModal.tsx` — modal para editar party size, notas, accesibilidad (sin hora)
- ✅ `ARRIVED` en tipos frontend + StatusPill + labels
- ✅ Escape key handler en side panel y modal
- ✅ Loading state por acción individual
- ✅ Mensajes de error/success claros con toast notifications
- ✅ Query invalidation tras cada acción

**Acciones funcionales en ReservationSidePanel:**
- ✅ Confirm reservation → `POST /{id}/confirm`
- ✅ Cancel reservation → `POST /{id}/cancel`
- ✅ Mark arrived → `POST /{id}/arrived`
- ✅ Mark seated → `POST /{id}/seat`
- ✅ Mark finished → `POST /{id}/complete`
- ✅ Mark no-show → `POST /{id}/no-show`
- ✅ Send confirmation SMS → `POST /{id}/notifications/confirmation`
- ✅ Send reminder SMS → `POST /{id}/notifications/reminder`
- ✅ Reassign table → `POST /{id}/assign`
- ✅ Edit details → `EditReservationModal` → `PATCH /{id}`

**Archivos nuevos:**
- `frontend/src/features/planning/components/EditReservationModal.tsx`

**Archivos modificados:**
- `frontend/src/features/planning/components/ReservationSidePanel.tsx` — wiring de edit → modal, Escape key
- `frontend/src/features/frontdesk/api/frontdeskApi.ts` — `arrivedReservation()`, `sendReservationReminder()`
- `frontend/src/features/frontdesk/types.ts` — `ARRIVED` status
- `frontend/src/features/frontdesk/components/StatusPill.tsx` — ARRIVED styling
- `frontend/src/features/frontdesk/utils/frontdeskUtils.ts` — ARRIVED label
- `frontend/src/features/planning/pages/PlanningPage.tsx` — ARRIVED visuals
- `backend/.../reservation/domain/ReservationStatus.java` — ARRIVED
- `backend/.../reservation/service/ReservationService.java` — `arrived()`, transition rules
- `backend/.../reservation/api/ReservationController.java` — POST /arrived
- `backend/.../notification/domain/NotificationTemplateCode.java` — RESERVATION_REMINDER
- `backend/.../notification/service/SmsNotificationService.java` — reminder message
- `backend/.../notification/api/ReservationSmsNotificationController.java` — POST /reminder

**Siguiente (Fase 2):**
- Crear reservas desde planning (`CreateReservationPanel`)
- Extraer `SlideOver` reutilizable de `InsightPanel` + `ReservationSidePanel`

---

## Apéndice E: Qué NO tocar todavía

- **Algoritmo de asignación** — Funciona, tiene tests, no necesita cambios UX ahora.
- **Backend endpoints de reserva** — Todos existen, incluyendo `/arrived` y `/send-reminder`.
- **Lógica de estado de reservas** — `ensureTransitionAllowed` en backend es correcta y completa.
- **Lógica de notificaciones** — El scheduler de reminders funciona. Solo añadimos llamada a `/notifications/confirmation`.
- **3D / drag-and-drop** — No están en el roadmap inmediato.
- **Multi-tenant** — La arquitectura actual es correcta, no necesita cambios.
- **Integraciones externas** — WhatsApp, Google, etc. son Fase futura.
