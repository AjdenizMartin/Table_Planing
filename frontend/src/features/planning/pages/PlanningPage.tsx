import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { InsightBar } from "@/features/ai/components/InsightBar";
import { InsightPanel } from "@/features/ai/components/InsightPanel";
import {
  useAiInsights,
  useAiInsightsSummary,
  useDismissAiInsight,
} from "@/features/ai/hooks/useAiInsights";
import { useAuth } from "@/features/auth/context/AuthContext";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import type { ReservationStatus } from "@/features/frontdesk/types";
import { normalizeTimeForInput, todayDateValue } from "@/features/frontdesk/utils/frontdeskUtils";
import * as planningApi from "@/features/planning/api/planningApi";
import type {
  AssignReservationResponse,
  PlanningDiningRoomResponse,
  PlanningReservationSummaryResponse,
  PlanningTableResponse,
} from "@/features/planning/types";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";

type ServiceWindow = "all" | "lunch" | "dinner";
type StatusFilter = "all" | ReservationStatus | "UNASSIGNED";

interface StatusVisual {
  label: string;
  tone: string;
  dot: string;
  tooltip: string;
  action: string;
}

const STATUS_VISUALS: Record<ReservationStatus | "FREE" | "CONFLICT" | "UNASSIGNED" | "CLEANING", StatusVisual> = {
  FREE: {
    label: "Free",
    tone: "border-emerald-300/25 bg-emerald-400/10 text-emerald-100",
    dot: "bg-emerald-300",
    tooltip: "Mesa libre en el contexto seleccionado.",
    action: "Asignar reserva disponible",
  },
  PENDING: {
    label: "Pending confirmation",
    tone: "border-amber-300/35 bg-amber-400/15 text-amber-100",
    dot: "bg-amber-300",
    tooltip: "Reserva pendiente de confirmar.",
    action: "Enviar recordatorio o llamar",
  },
  CONFIRMED: {
    label: "Confirmed",
    tone: "border-sky-300/35 bg-sky-400/15 text-sky-100",
    dot: "bg-sky-300",
    tooltip: "Reserva confirmada.",
    action: "Preparar mesa",
  },
  SEATED: {
    label: "In service",
    tone: "border-emerald-300/35 bg-emerald-400/15 text-emerald-100",
    dot: "bg-emerald-300",
    tooltip: "Cliente sentado o en servicio.",
    action: "Marcar finalizada cuando termine",
  },
  COMPLETED: {
    label: "Finished",
    tone: "border-slate-300/25 bg-slate-400/10 text-slate-100",
    dot: "bg-slate-300",
    tooltip: "Reserva finalizada.",
    action: "Liberar o limpiar mesa",
  },
  CANCELLED: {
    label: "Cancelled",
    tone: "border-rose-300/30 bg-rose-400/10 text-rose-100",
    dot: "bg-rose-300",
    tooltip: "Reserva cancelada.",
    action: "No ocupa mesa",
  },
  NO_SHOW: {
    label: "No-show",
    tone: "border-fuchsia-300/30 bg-fuchsia-400/10 text-fuchsia-100",
    dot: "bg-fuchsia-300",
    tooltip: "Cliente no presentado.",
    action: "Registrar historial",
  },
  CONFLICT: {
    label: "Conflict",
    tone: "border-red-300/45 bg-red-500/15 text-red-100",
    dot: "bg-red-300",
    tooltip: "Solapamiento o regla incumplida.",
    action: "Resolver conflicto",
  },
  UNASSIGNED: {
    label: "Unassigned",
    tone: "border-violet-300/35 bg-violet-400/15 text-violet-100",
    dot: "bg-violet-300",
    tooltip: "Reserva sin mesa asignada.",
    action: "Asignar mesa",
  },
  CLEANING: {
    label: "Cleaning",
    tone: "border-cyan-300/35 bg-cyan-400/15 text-cyan-100",
    dot: "bg-cyan-300",
    tooltip: "Buffer de limpieza entre reservas.",
    action: "Preparar siguiente reserva",
  },
};

const SERVICE_WINDOWS: Record<ServiceWindow, { label: string; start: number; end: number }> = {
  all: { label: "Todo el dia", start: 0, end: 24 * 60 },
  lunch: { label: "Comida", start: 11 * 60, end: 17 * 60 },
  dinner: { label: "Cena", start: 17 * 60, end: 24 * 60 },
};

const TIMELINE_START = 11 * 60;
const TIMELINE_END = 24 * 60;

function timeToMinutes(value: string | null) {
  if (!value) {
    return 0;
  }
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function isInsideServiceWindow(reservation: PlanningReservationSummaryResponse, serviceWindow: ServiceWindow) {
  const window = SERVICE_WINDOWS[serviceWindow];
  const start = timeToMinutes(reservation.startTime);
  return start >= window.start && start < window.end;
}

function reservationName(reservation: PlanningReservationSummaryResponse) {
  return reservation.customerName || `Reservation #${reservation.reservationId}`;
}

function tableName(table: PlanningTableResponse) {
  return table.label ? `${table.code} · ${table.label}` : table.code;
}

function getReservationVisual(reservation: PlanningReservationSummaryResponse) {
  return STATUS_VISUALS[reservation.status] ?? STATUS_VISUALS.FREE;
}

function getTablePrimaryReservation(table: PlanningTableResponse, serviceWindow: ServiceWindow) {
  return table.reservations
    .filter((reservation) => isInsideServiceWindow(reservation, serviceWindow))
    .sort((left, right) => timeToMinutes(left.startTime) - timeToMinutes(right.startTime))[0] ?? null;
}

function getTableVisual(table: PlanningTableResponse, serviceWindow: ServiceWindow) {
  const reservation = getTablePrimaryReservation(table, serviceWindow);
  if (!table.active) {
    return {
      label: "Blocked",
      tone: "border-slate-500/40 bg-slate-800/70 text-slate-300",
      dot: "bg-slate-500",
      tooltip: "Mesa inactiva o bloqueada.",
      action: "Revisar configuracion",
    };
  }
  return reservation ? getReservationVisual(reservation) : STATUS_VISUALS.FREE;
}

function formatRange(reservation: PlanningReservationSummaryResponse) {
  const start = normalizeTimeForInput(reservation.startTime);
  const end = normalizeTimeForInput(reservation.endTime);
  return end ? `${start} - ${end}` : start;
}

function pct(value: number, total: number) {
  if (total <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(100, (value / total) * 100));
}

function StatCard({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return (
    <div className="rounded-3xl border border-white/10 bg-slate-950/45 px-4 py-3 shadow-lg shadow-black/10">
      <p className="text-[0.65rem] font-semibold uppercase tracking-[0.22em] text-slate-500">
        {label}
      </p>
      <p className="mt-2 text-2xl font-semibold text-white">{value}</p>
      <p className="mt-1 text-xs text-slate-400">{detail}</p>
    </div>
  );
}

function StatusLegend() {
  const items: Array<ReservationStatus | "FREE" | "UNASSIGNED" | "CONFLICT" | "CLEANING"> = [
    "FREE",
    "CONFIRMED",
    "PENDING",
    "SEATED",
    "CLEANING",
    "UNASSIGNED",
    "CONFLICT",
  ];

  return (
    <div className="flex flex-wrap gap-2">
      {items.map((status) => {
        const visual = STATUS_VISUALS[status];
        return (
          <span
            key={status}
            title={`${visual.tooltip} Action: ${visual.action}`}
            className={[
              "inline-flex h-9 items-center gap-2 rounded-full border px-3 text-xs font-semibold",
              visual.tone,
            ].join(" ")}
          >
            <span className={["h-2 w-2 rounded-full", visual.dot].join(" ")} />
            {visual.label}
          </span>
        );
      })}
    </div>
  );
}

export function PlanningPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedDate, setSelectedDate] = useState(todayDateValue());
  const [serviceWindow, setServiceWindow] = useState<ServiceWindow>("dinner");
  const [selectedRoomId, setSelectedRoomId] = useState<"all" | number>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedReservationId, setSelectedReservationId] = useState<number | null>(null);
  const [selectedTableId, setSelectedTableId] = useState<number | null>(null);
  const [lastAssignResponse, setLastAssignResponse] = useState<AssignReservationResponse | null>(null);
  const [insightPanelOpen, setInsightPanelOpen] = useState(false);

  const aiInsightsQuery = useAiInsights(selectedDate);
  const aiSummaryQuery = useAiInsightsSummary(selectedDate);
  const dismissInsightMutation = useDismissAiInsight(selectedDate);

  const activeRestaurant = session.restaurants.find((restaurant) => restaurant.id === activeRestaurantId);
  const canDismissInsights = activeRestaurant?.roles.some((role) =>
    role === "PLATFORM_ADMIN" || role === "RESTAURANT_OWNER" || role === "MANAGER",
  ) ?? false;

  const planningQuery = useQuery({
    queryKey: ["planning", activeRestaurantId, selectedDate],
    queryFn: () => planningApi.getPlanning(activeRestaurantId!, selectedDate),
    enabled: activeRestaurantId !== null,
    retry: 1,
  });

  const recalculateMutation = useMutation({
    mutationFn: () => planningApi.recalculatePlanning(activeRestaurantId!, selectedDate),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["planning", activeRestaurantId, selectedDate] });
      await queryClient.invalidateQueries({ queryKey: ["aiInsights", activeRestaurantId, selectedDate] });
      await queryClient.invalidateQueries({ queryKey: ["aiInsights", activeRestaurantId, selectedDate, "summary"] });
    },
  });

  const assignMutation = useMutation({
    mutationFn: (reservationId: number) =>
      planningApi.assignReservationAutomatically(activeRestaurantId!, reservationId),
    onSuccess: async (response) => {
      setLastAssignResponse(response);
      await queryClient.invalidateQueries({ queryKey: ["planning", activeRestaurantId, selectedDate] });
      await queryClient.invalidateQueries({ queryKey: ["aiInsights", activeRestaurantId, selectedDate] });
      await queryClient.invalidateQueries({ queryKey: ["aiInsights", activeRestaurantId, selectedDate, "summary"] });
    },
  });

  const planning = planningQuery.data;
  const insights = aiInsightsQuery.data ?? [];
  const allReservations = useMemo(() => {
    if (!planning) {
      return [];
    }
    return [...planning.assignedReservations, ...planning.unassignedReservations]
      .filter((reservation) => isInsideServiceWindow(reservation, serviceWindow))
      .sort((left, right) => timeToMinutes(left.startTime) - timeToMinutes(right.startTime));
  }, [planning, serviceWindow]);

  const filteredReservations = useMemo(() => {
    const normalizedSearch = searchQuery.trim().toLowerCase();
    return allReservations.filter((reservation) => {
      const isUnassigned = reservation.tableId === null && reservation.tableCombinationId === null;
      const statusMatches =
        statusFilter === "all" ||
        reservation.status === statusFilter ||
        (statusFilter === "UNASSIGNED" && isUnassigned);
      const searchMatches =
        normalizedSearch.length === 0 ||
        reservationName(reservation).toLowerCase().includes(normalizedSearch) ||
        String(reservation.partySize).includes(normalizedSearch) ||
        reservation.startTime.includes(normalizedSearch);
      return statusMatches && searchMatches;
    });
  }, [allReservations, searchQuery, statusFilter]);

  const visibleRooms = useMemo(() => {
    const rooms = planning?.diningRooms ?? [];
    if (selectedRoomId === "all") {
      return rooms;
    }
    return rooms.filter((room) => room.id === selectedRoomId);
  }, [planning?.diningRooms, selectedRoomId]);

  const selectedReservation =
    allReservations.find((reservation) => reservation.reservationId === selectedReservationId) ?? null;
  const selectedTable = visibleRooms
    .flatMap((room) => room.tables)
    .find((table) => table.id === selectedTableId) ?? null;

  const stats = useMemo(() => {
    const totalSeats = visibleRooms.flatMap((room) => room.tables).reduce((sum, table) => sum + table.maxCapacity, 0);
    const assignedReservations = allReservations.filter((reservation) => reservation.tableId !== null || reservation.tableCombinationId !== null);
    const occupiedSeats = assignedReservations.reduce((sum, reservation) => sum + reservation.partySize, 0);
    const pending = allReservations.filter((reservation) => reservation.status === "PENDING").length;
    const unassigned = allReservations.filter((reservation) => reservation.tableId === null && reservation.tableCombinationId === null).length;
    return {
      totalSeats,
      occupiedSeats,
      occupancy: totalSeats > 0 ? Math.round((occupiedSeats / totalSeats) * 100) : 0,
      reservations: allReservations.length,
      guests: allReservations.reduce((sum, reservation) => sum + reservation.partySize, 0),
      pending,
      unassigned,
    };
  }, [allReservations, visibleRooms]);

  return (
    <section className="grid gap-6">
      <header className="overflow-hidden rounded-[2rem] border border-white/10 bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.24),transparent_34%),linear-gradient(135deg,rgba(15,23,42,0.96),rgba(2,6,23,0.92))] p-5 shadow-2xl shadow-black/30 sm:p-7">
        <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.32em] text-brand-300">
              Live Planning Command Center
            </p>
            <h1 className="mt-3 max-w-4xl text-3xl font-semibold tracking-tight text-white sm:text-5xl">
              Plano visual de salones, mesas y reservas
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300">
              Reasigna mesas y mejora ocupacion sin cambiar nunca la hora original de una reserva.
              La timeline es de solo lectura para proteger la hora acordada con el cliente.
            </p>
          </div>

          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              className="h-12 rounded-2xl bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400"
              onClick={() => navigate(`/reservations?mode=new&date=${encodeURIComponent(selectedDate)}`)}
            >
              New reservation
            </button>
            <button
              type="button"
              className="h-12 rounded-2xl border border-white/10 bg-white/10 px-5 text-sm font-semibold text-white transition hover:border-brand-300/40 hover:bg-brand-500/10 disabled:opacity-60"
              disabled={recalculateMutation.isPending || activeRestaurantId === null}
              onClick={() => {
                void recalculateMutation.mutateAsync();
              }}
            >
              {recalculateMutation.isPending ? "Optimizing..." : "Optimize table allocation"}
            </button>
            <button
              type="button"
              className="h-12 rounded-2xl border border-white/10 bg-white/10 px-5 text-sm font-semibold text-white transition hover:border-brand-300/40 hover:bg-brand-500/10"
              onClick={() => navigate("/settings/layout")}
            >
              Edit layout
            </button>
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          <label className="grid gap-2 rounded-3xl border border-white/10 bg-slate-950/40 p-3">
            <span className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">
              Fecha
            </span>
            <input
              className="h-12 rounded-2xl border border-white/10 bg-slate-950/70 px-4 text-white outline-none focus:border-brand-300/70"
              type="date"
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </label>

          <label className="grid gap-2 rounded-3xl border border-white/10 bg-slate-950/40 p-3">
            <span className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">
              Turno
            </span>
            <select
              className="h-12 rounded-2xl border border-white/10 bg-slate-950/70 px-4 text-white outline-none focus:border-brand-300/70"
              value={serviceWindow}
              onChange={(event) => setServiceWindow(event.target.value as ServiceWindow)}
            >
              {Object.entries(SERVICE_WINDOWS).map(([value, window]) => (
                <option key={value} value={value}>
                  {window.label}
                </option>
              ))}
            </select>
          </label>

          <label className="grid gap-2 rounded-3xl border border-white/10 bg-slate-950/40 p-3 md:col-span-2">
            <span className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">
              Salon
            </span>
            <select
              className="h-12 rounded-2xl border border-white/10 bg-slate-950/70 px-4 text-white outline-none focus:border-brand-300/70"
              value={selectedRoomId}
              onChange={(event) =>
                setSelectedRoomId(event.target.value === "all" ? "all" : Number(event.target.value))
              }
            >
              <option value="all">Todos los salones</option>
              {(planning?.diningRooms ?? []).map((room) => (
                <option key={room.id} value={room.id}>
                  {room.name}
                </option>
              ))}
            </select>
          </label>

          <StatCard label="Ocupacion" value={`${stats.occupancy}%`} detail={`${stats.occupiedSeats}/${stats.totalSeats} seats`} />
          <StatCard label="Realtime" value="Connected" detail="REST + WebSocket ready" />
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <StatCard label="Reservas" value={stats.reservations} detail="En el turno seleccionado" />
          <StatCard label="Comensales" value={stats.guests} detail="Total previsto" />
          <StatCard label="Pendientes" value={stats.pending} detail="Requieren confirmacion" />
          <StatCard label="Sin asignar" value={stats.unassigned} detail="Necesitan mesa" />
        </div>
      </header>

      <StatusLegend />

      <InsightBar
        summary={aiSummaryQuery.data}
        insights={insights}
        onOpenPanel={() => setInsightPanelOpen(true)}
      />

      {planningQuery.isLoading ? (
        <div className="rounded-[2rem] border border-white/10 bg-white/5 p-8 text-slate-300">
          Cargando planning del dia...
        </div>
      ) : null}

      {planningQuery.error ? (
        <div className="rounded-[2rem] border border-rose-300/30 bg-rose-500/10 p-5 text-rose-100">
          {getErrorMessage(planningQuery.error)}
        </div>
      ) : null}

      {planning ? (
        <>
          <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)_360px]">
            <ReservationQueue
              reservations={filteredReservations}
              statusFilter={statusFilter}
              searchQuery={searchQuery}
              selectedReservationId={selectedReservationId}
              onStatusFilterChange={setStatusFilter}
              onSearchQueryChange={setSearchQuery}
              onSelectReservation={(reservationId) => {
                setSelectedReservationId(reservationId);
                setSelectedTableId(null);
                setLastAssignResponse(null);
              }}
              onAssignAutomatically={(reservationId) => {
                setSelectedReservationId(reservationId);
                void assignMutation.mutateAsync(reservationId);
              }}
              assigningReservationId={assignMutation.isPending ? selectedReservationId : null}
            />

            <FloorPlan
              rooms={visibleRooms}
              serviceWindow={serviceWindow}
              selectedTableId={selectedTableId}
              selectedReservationId={selectedReservationId}
              onSelectTable={(tableId) => {
                setSelectedTableId(tableId);
                setSelectedReservationId(null);
                setLastAssignResponse(null);
              }}
              onSelectReservation={(reservationId) => {
                setSelectedReservationId(reservationId);
                setSelectedTableId(null);
                setLastAssignResponse(null);
              }}
            />

            <DetailPanel
              selectedReservation={selectedReservation}
              selectedTable={selectedTable}
              assignResponse={
                lastAssignResponse?.reservationId === selectedReservation?.reservationId
                  ? lastAssignResponse
                  : null
              }
              onAssignAutomatically={(reservationId) => {
                setSelectedReservationId(reservationId);
                void assignMutation.mutateAsync(reservationId);
              }}
              assignPending={assignMutation.isPending}
              assignError={assignMutation.error}
            />
          </div>

          <ReadOnlyTimeline
            rooms={visibleRooms}
            serviceWindow={serviceWindow}
            selectedReservationId={selectedReservationId}
            onSelectReservation={(reservationId) => {
              setSelectedReservationId(reservationId);
              setSelectedTableId(null);
              setLastAssignResponse(null);
            }}
          />

          {planning.conflicts.length > 0 ? (
            <section className="rounded-[2rem] border border-red-300/25 bg-red-500/10 p-5">
              <h2 className="text-xl font-semibold text-red-100">Conflictos detectados</h2>
              <div className="mt-4 grid gap-3 md:grid-cols-2">
                {planning.conflicts.map((conflict, index) => (
                  <article key={`${conflict.resourceId}-${index}`} className="rounded-3xl border border-red-300/20 bg-slate-950/40 p-4 text-red-100">
                    <p className="font-semibold">{conflict.resourceLabel}</p>
                    <p className="mt-2 text-sm">{conflict.message}</p>
                    <p className="mt-2 text-xs uppercase tracking-[0.2em] text-red-200/80">
                      {normalizeTimeForInput(conflict.overlappingStart)} - {normalizeTimeForInput(conflict.overlappingEnd)}
                    </p>
                  </article>
                ))}
              </div>
            </section>
          ) : null}
        </>
      ) : null}

      <InsightPanel
        insights={insights}
        open={insightPanelOpen}
        onClose={() => setInsightPanelOpen(false)}
        onDismiss={(insightId) => dismissInsightMutation.mutate(insightId)}
        dismissingInsightId={dismissInsightMutation.variables ?? null}
        canDismiss={canDismissInsights}
      />
    </section>
  );
}

function ReservationQueue({
  reservations,
  statusFilter,
  searchQuery,
  selectedReservationId,
  onStatusFilterChange,
  onSearchQueryChange,
  onSelectReservation,
  onAssignAutomatically,
  assigningReservationId,
}: {
  reservations: PlanningReservationSummaryResponse[];
  statusFilter: StatusFilter;
  searchQuery: string;
  selectedReservationId: number | null;
  onStatusFilterChange: (filter: StatusFilter) => void;
  onSearchQueryChange: (query: string) => void;
  onSelectReservation: (reservationId: number) => void;
  onAssignAutomatically: (reservationId: number) => void;
  assigningReservationId: number | null;
}) {
  const filters: StatusFilter[] = ["all", "CONFIRMED", "PENDING", "SEATED", "UNASSIGNED"];

  return (
    <aside className="rounded-[2rem] border border-white/10 bg-slate-950/70 p-4 shadow-2xl shadow-black/20">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.25em] text-brand-300">
            Reservas
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-white">Cola del dia</h2>
        </div>
        <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-300">
          {reservations.length}
        </span>
      </div>

      <input
        className="mt-4 h-12 w-full rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-sm text-white outline-none placeholder:text-slate-500 focus:border-brand-300/70"
        value={searchQuery}
        onChange={(event) => onSearchQueryChange(event.target.value)}
        placeholder="Buscar nombre, hora o pax"
      />

      <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
        {filters.map((filter) => (
          <button
            key={filter}
            type="button"
            className={[
              "h-10 shrink-0 rounded-full border px-3 text-xs font-semibold transition",
              statusFilter === filter
                ? "border-brand-300/60 bg-brand-400 text-slate-950"
                : "border-white/10 bg-white/5 text-slate-300 hover:border-brand-300/40",
            ].join(" ")}
            onClick={() => onStatusFilterChange(filter)}
          >
            {filter === "all" ? "All" : filter.replace("_", " ")}
          </button>
        ))}
      </div>

      <div className="mt-4 grid max-h-[680px] gap-3 overflow-y-auto pr-1">
        {reservations.map((reservation) => {
          const isUnassigned = reservation.tableId === null && reservation.tableCombinationId === null;
          return (
            <button
              key={reservation.reservationId}
              type="button"
              className={[
                "rounded-3xl border p-4 text-left transition",
                selectedReservationId === reservation.reservationId
                  ? "border-brand-300/70 bg-brand-500/15 shadow-lg shadow-brand-950/20"
                  : "border-white/10 bg-white/5 hover:border-brand-300/40 hover:bg-white/10",
              ].join(" ")}
              onClick={() => onSelectReservation(reservation.reservationId)}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-base font-semibold text-white">{reservationName(reservation)}</p>
                  <p className="mt-1 text-sm text-slate-400">
                    {normalizeTimeForInput(reservation.startTime)} · {reservation.partySize} pax
                  </p>
                </div>
                <StatusPill status={reservation.status} />
              </div>
              <div className="mt-3 flex items-center justify-between gap-3">
                <span className={[
                  "rounded-full border px-3 py-1 text-xs font-semibold",
                  isUnassigned ? STATUS_VISUALS.UNASSIGNED.tone : "border-white/10 bg-slate-950/60 text-slate-300",
                ].join(" ")}>
                  {isUnassigned ? "Sin mesa" : reservation.tableCode ?? reservation.tableCombinationName}
                </span>
                {isUnassigned ? (
                  <span
                    role="button"
                    tabIndex={0}
                    className="rounded-full bg-brand-500 px-3 py-1 text-xs font-semibold text-slate-950"
                    onClick={(event) => {
                      event.stopPropagation();
                      onAssignAutomatically(reservation.reservationId);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        event.stopPropagation();
                        onAssignAutomatically(reservation.reservationId);
                      }
                    }}
                  >
                    {assigningReservationId === reservation.reservationId ? "Asignando" : "Find best table"}
                  </span>
                ) : null}
              </div>
            </button>
          );
        })}

        {reservations.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/5 p-5 text-sm text-slate-300">
            No hay reservas que coincidan con los filtros.
          </div>
        ) : null}
      </div>
    </aside>
  );
}

function FloorPlan({
  rooms,
  serviceWindow,
  selectedTableId,
  selectedReservationId,
  onSelectTable,
  onSelectReservation,
}: {
  rooms: PlanningDiningRoomResponse[];
  serviceWindow: ServiceWindow;
  selectedTableId: number | null;
  selectedReservationId: number | null;
  onSelectTable: (tableId: number) => void;
  onSelectReservation: (reservationId: number) => void;
}) {
  return (
    <main className="grid gap-5">
      {rooms.map((room) => (
        <section key={room.id} className="overflow-hidden rounded-[2rem] border border-white/10 bg-[linear-gradient(145deg,rgba(15,23,42,0.86),rgba(2,6,23,0.9))] shadow-2xl shadow-black/20">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 px-5 py-4">
            <div>
              <h2 className="text-2xl font-semibold text-white">{room.name}</h2>
              <p className="mt-1 text-sm text-slate-400">
                Prioridad {room.priority} · {room.accessible ? "Accesible" : "No accesible / escalera"} · {room.tables.length} mesas
              </p>
            </div>
            <span className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-300">
              Service mode
            </span>
          </div>

          <div className="relative min-h-[520px] overflow-hidden bg-[radial-gradient(circle_at_25%_10%,rgba(45,212,191,0.15),transparent_28%),linear-gradient(135deg,rgba(30,41,59,0.42),rgba(15,23,42,0.76))] p-4 sm:p-6">
            <div className="absolute inset-0 opacity-30 [background-image:linear-gradient(rgba(255,255,255,0.05)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.05)_1px,transparent_1px)] [background-size:32px_32px]" />
            <div className="absolute left-8 top-8 rounded-full border border-white/10 bg-slate-950/60 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
              Floor plan
            </div>

            {room.tables.map((table) => {
              const visual = getTableVisual(table, serviceWindow);
              const primaryReservation = getTablePrimaryReservation(table, serviceWindow);
              const isSelected = selectedTableId === table.id || primaryReservation?.reservationId === selectedReservationId;
              return (
                <button
                  key={table.id}
                  type="button"
                  title={visual.tooltip}
                  className={[
                    "absolute flex min-h-24 flex-col justify-between rounded-[1.6rem] border p-3 text-left shadow-2xl shadow-black/25 transition duration-200 hover:-translate-y-1 hover:shadow-brand-950/20",
                    visual.tone,
                    isSelected ? "ring-2 ring-white/80" : "",
                  ].join(" ")}
                  style={{
                    left: `${pct(table.x, room.layoutWidth)}%`,
                    top: `${pct(table.y, room.layoutHeight)}%`,
                    width: `${Math.max(pct(table.width, room.layoutWidth), 13)}%`,
                    height: `${Math.max(pct(table.height, room.layoutHeight), 12)}%`,
                  }}
                  onClick={() => onSelectTable(table.id)}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-base font-black tracking-tight">{table.code}</p>
                      <p className="text-xs opacity-80">{table.minCapacity}-{table.maxCapacity} pax</p>
                    </div>
                    <span className={["mt-1 h-2.5 w-2.5 rounded-full", visual.dot].join(" ")} />
                  </div>

                  {primaryReservation ? (
                    <span
                      role="button"
                      tabIndex={0}
                      className="mt-3 rounded-2xl bg-slate-950/35 px-3 py-2 text-xs font-semibold backdrop-blur transition hover:bg-slate-950/55"
                      onClick={(event) => {
                        event.stopPropagation();
                        onSelectReservation(primaryReservation.reservationId);
                      }}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          event.stopPropagation();
                          onSelectReservation(primaryReservation.reservationId);
                        }
                      }}
                    >
                      {normalizeTimeForInput(primaryReservation.startTime)} · {reservationName(primaryReservation)}
                    </span>
                  ) : (
                    <span className="mt-3 rounded-2xl bg-slate-950/30 px-3 py-2 text-xs font-semibold">
                      Available
                    </span>
                  )}
                </button>
              );
            })}

            {room.tables.length === 0 ? (
              <div className="relative z-10 rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
                Este salon todavia no tiene mesas configuradas.
              </div>
            ) : null}
          </div>
        </section>
      ))}

      {rooms.length === 0 ? (
        <section className="rounded-[2rem] border border-white/10 bg-white/5 p-8 text-slate-300">
          No hay salones para el filtro seleccionado.
        </section>
      ) : null}
    </main>
  );
}

function NoAssignmentCard({ response }: { response: AssignReservationResponse }) {
  return (
    <div className="rounded-3xl border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-50">
      <p className="font-semibold text-white">No se puede asignar a la hora solicitada</p>
      <p className="mt-2 text-rose-100/90">
        El sistema ha revisado mesas individuales y combinaciones activas, pero ninguna cumple todas las condiciones para esa franja.
      </p>

      {response.reasons.length > 0 ? (
        <ul className="mt-3 grid gap-2">
          {response.reasons.map((reason) => (
            <li key={reason} className="rounded-2xl border border-rose-200/15 bg-slate-950/35 px-3 py-2">
              {humanizeAssignmentReason(reason)}
            </li>
          ))}
        </ul>
      ) : null}

      <div className="mt-4 rounded-2xl border border-brand-300/30 bg-brand-400/10 p-3 text-brand-50">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-200">
          Proxima opcion
        </p>
        {response.recommendedStartTime ? (
          <>
            <p className="mt-2 text-2xl font-semibold text-white">
              {normalizeTimeForInput(response.recommendedStartTime)}
            </p>
            <p className="mt-1 text-sm text-brand-100">
              Hay una mesa posible a esa hora. No se ha cambiado la reserva automaticamente; si el cliente acepta, edita la hora manualmente.
            </p>
          </>
        ) : (
          <p className="mt-2 text-sm text-brand-100">
            No se encontro una opcion posterior en el mismo dia. Prueba otra fecha, otro turno o una combinacion de mesas adicional.
          </p>
        )}
      </div>
    </div>
  );
}

function humanizeAssignmentReason(reason: string) {
  return reason
    .replace(/candidate\(s\) rejected due to insufficient_capacity/g, "opciones descartadas por capacidad insuficiente")
    .replace(/candidate\(s\) rejected due to below_min_capacity/g, "opciones descartadas por capacidad minima demasiado alta")
    .replace(/candidate\(s\) rejected due to time_overlap/g, "opciones descartadas por solapamiento con otra reserva")
    .replace(/candidate\(s\) rejected due to accessibility_mismatch/g, "opciones descartadas por accesibilidad")
    .replace(/candidate\(s\) rejected due to inactive_table/g, "opciones descartadas por mesa inactiva")
    .replace(/candidate\(s\) rejected due to inactive_dining_room/g, "opciones descartadas por salon inactivo")
    .replace(/candidate\(s\) rejected due to inactive_combination/g, "opciones descartadas por combinacion inactiva")
    .replace("No active tables or combinations are configured for this restaurant", "No hay mesas ni combinaciones activas configuradas")
    .replace("No candidate satisfied the hard constraints", "Ninguna mesa o combinacion cumple las reglas obligatorias");
}

function DetailPanel({
  selectedReservation,
  selectedTable,
  assignResponse,
  onAssignAutomatically,
  assignPending,
  assignError,
}: {
  selectedReservation: PlanningReservationSummaryResponse | null;
  selectedTable: PlanningTableResponse | null;
  assignResponse: AssignReservationResponse | null;
  onAssignAutomatically: (reservationId: number) => void;
  assignPending: boolean;
  assignError: unknown;
}) {
  return (
    <aside className="rounded-[2rem] border border-white/10 bg-slate-950/70 p-5 shadow-2xl shadow-black/20">
      <p className="text-xs font-semibold uppercase tracking-[0.25em] text-brand-300">
        Inspector
      </p>

      {assignError ? (
        <div className="mt-4 rounded-3xl border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
          {getErrorMessage(assignError)}
        </div>
      ) : null}

      {selectedReservation ? (
        <div className="mt-4 grid gap-4">
          <div>
            <h2 className="text-3xl font-semibold text-white">{reservationName(selectedReservation)}</h2>
            <p className="mt-2 text-sm text-slate-400">
              {selectedReservation.partySize} pax · {formatRange(selectedReservation)}
            </p>
          </div>
          <StatusPill status={selectedReservation.status} />
          <div className="grid gap-3 rounded-3xl border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
            <p>Mesa: {selectedReservation.tableCode ?? selectedReservation.tableCombinationName ?? "Sin asignar"}</p>
            <p>Buffer limpieza: {selectedReservation.cleaningBufferMin} min</p>
            <p>Accesibilidad: {selectedReservation.accessibilityRequired ? "Requerida" : "No indicada"}</p>
            <p>Notas: {selectedReservation.specialRequests || "Sin notas"}</p>
          </div>
          <div className="rounded-3xl border border-amber-300/25 bg-amber-400/10 p-4 text-sm text-amber-100">
            La hora de esta reserva no se puede cambiar desde el planning. Usa edicion manual solo si el cliente lo solicita.
          </div>
          {assignResponse && !assignResponse.assigned ? (
            <NoAssignmentCard response={assignResponse} />
          ) : null}
          {selectedReservation.tableId === null && selectedReservation.tableCombinationId === null ? (
            <button
              type="button"
              className="h-12 rounded-2xl bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
              disabled={assignPending}
              onClick={() => onAssignAutomatically(selectedReservation.reservationId)}
            >
              {assignPending ? "Asignando..." : "Find best table"}
            </button>
          ) : null}
        </div>
      ) : selectedTable ? (
        <div className="mt-4 grid gap-4">
          <div>
            <h2 className="text-3xl font-semibold text-white">{tableName(selectedTable)}</h2>
            <p className="mt-2 text-sm text-slate-400">
              Capacidad {selectedTable.minCapacity}-{selectedTable.maxCapacity} pax
            </p>
          </div>
          <div className="grid gap-3">
            {selectedTable.reservations.length > 0 ? (
              selectedTable.reservations.map((reservation) => (
                <div key={reservation.reservationId} className="rounded-3xl border border-white/10 bg-white/5 p-4">
                  <p className="font-semibold text-white">{reservationName(reservation)}</p>
                  <p className="mt-1 text-sm text-slate-400">
                    {formatRange(reservation)} · {reservation.partySize} pax
                  </p>
                  <div className="mt-3">
                    <StatusPill status={reservation.status} />
                  </div>
                </div>
              ))
            ) : (
              <div className="rounded-3xl border border-emerald-300/25 bg-emerald-400/10 p-4 text-sm text-emerald-100">
                Mesa libre para el turno seleccionado.
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="mt-4 rounded-3xl border border-white/10 bg-white/5 p-5 text-sm text-slate-300">
          Toca una mesa o una reserva para ver detalles, acciones y alertas.
        </div>
      )}
    </aside>
  );
}

function ReadOnlyTimeline({
  rooms,
  serviceWindow,
  selectedReservationId,
  onSelectReservation,
}: {
  rooms: PlanningDiningRoomResponse[];
  serviceWindow: ServiceWindow;
  selectedReservationId: number | null;
  onSelectReservation: (reservationId: number) => void;
}) {
  const tables = rooms.flatMap((room) => room.tables.map((table) => ({ ...table, roomName: room.name })));
  const hourMarks = Array.from({ length: 14 }, (_, index) => 11 + index);
  const totalMinutes = TIMELINE_END - TIMELINE_START;

  return (
    <section className="rounded-[2rem] border border-white/10 bg-slate-950/70 p-5 shadow-2xl shadow-black/20">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.25em] text-brand-300">
            Timeline read-only
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-white">Horario protegido</h2>
        </div>
        <span className="rounded-full border border-amber-300/25 bg-amber-400/10 px-4 py-2 text-xs font-semibold text-amber-100">
          Drag horizontal desactivado. Las horas no se cambian aqui.
        </span>
      </div>

      <div className="mt-5 overflow-x-auto">
        <div className="min-w-[980px]">
          <div className="grid grid-cols-[180px_minmax(760px,1fr)] border-b border-white/10 pb-2">
            <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">Mesa</div>
            <div className="relative grid" style={{ gridTemplateColumns: `repeat(${hourMarks.length}, minmax(0, 1fr))` }}>
              {hourMarks.map((hour) => (
                <div key={hour} className="border-l border-white/10 pl-2 text-xs font-semibold text-slate-500">
                  {String(hour).padStart(2, "0")}:00
                </div>
              ))}
            </div>
          </div>

          <div className="grid gap-2 pt-3">
            {tables.map((table) => (
              <div key={table.id} className="grid grid-cols-[180px_minmax(760px,1fr)] items-center gap-3">
                <div className="rounded-2xl border border-white/10 bg-white/5 px-3 py-3">
                  <p className="text-sm font-semibold text-white">{table.code}</p>
                  <p className="mt-1 text-xs text-slate-500">{table.roomName}</p>
                </div>
                <div className="relative h-16 overflow-hidden rounded-2xl border border-white/10 bg-white/[0.03]">
                  <div className="absolute inset-0 grid" style={{ gridTemplateColumns: `repeat(${hourMarks.length}, minmax(0, 1fr))` }}>
                    {hourMarks.map((hour) => (
                      <div key={`${table.id}-${hour}`} className="border-l border-white/10" />
                    ))}
                  </div>
                  {table.reservations
                    .filter((reservation) => isInsideServiceWindow(reservation, serviceWindow))
                    .map((reservation) => {
                      const start = Math.max(timeToMinutes(reservation.startTime), TIMELINE_START);
                      const end = Math.min(timeToMinutes(reservation.endTime), TIMELINE_END);
                      const effectiveEnd = Math.min(timeToMinutes(reservation.effectiveEndTime), TIMELINE_END);
                      const left = pct(start - TIMELINE_START, totalMinutes);
                      const width = Math.max(pct(end - start, totalMinutes), 4);
                      const bufferWidth = Math.max(pct(effectiveEnd - end, totalMinutes), 0);
                      const visual = getReservationVisual(reservation);
                      return (
                        <button
                          key={`${table.id}-${reservation.reservationId}`}
                          type="button"
                          className={[
                            "absolute top-2 h-12 rounded-2xl border px-3 text-left text-xs font-semibold shadow-lg transition hover:ring-2 hover:ring-white/70",
                            visual.tone,
                            selectedReservationId === reservation.reservationId ? "ring-2 ring-white" : "",
                          ].join(" ")}
                          style={{ left: `${left}%`, width: `${width}%` }}
                          onClick={() => onSelectReservation(reservation.reservationId)}
                        >
                          <span className="line-clamp-1">{normalizeTimeForInput(reservation.startTime)} · {reservationName(reservation)}</span>
                          {bufferWidth > 0 ? (
                            <span
                              className="absolute bottom-0 right-0 top-0 rounded-r-2xl bg-cyan-300/20"
                              style={{ width: `${Math.min(bufferWidth * (100 / width), 35)}%` }}
                              title="Cleaning buffer"
                            />
                          ) : null}
                        </button>
                      );
                    })}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
