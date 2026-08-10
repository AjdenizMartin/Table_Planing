import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Map, Plus, RefreshCw } from "lucide-react";
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
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import { ReservationSidePanel } from "@/features/planning/components/ReservationSidePanel";
import { ReservationCreateModal } from "@/features/planning/components/ReservationCreateModal";
import { NeedsAttentionPanel } from "@/features/planning/components/NeedsAttentionPanel";
import { useI18n } from "@/features/i18n/I18nProvider";

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
    tooltip: "Table available for the selected service.",
    action: "Assign available reservation",
  },
  PENDING: {
    label: "Pending",
    tone: "border-amber-300/35 bg-amber-400/15 text-amber-100",
    dot: "bg-amber-300",
    tooltip: "Reservation awaiting confirmation.",
    action: "Send a reminder or call",
  },
  CONFIRMED: {
    label: "Confirmed",
    tone: "border-sky-300/35 bg-sky-400/15 text-sky-100",
    dot: "bg-sky-300",
    tooltip: "Confirmed reservation.",
    action: "Prepare table",
  },
  ARRIVED: {
    label: "Arrived",
    tone: "border-teal-300/35 bg-teal-400/15 text-teal-100",
    dot: "bg-teal-300",
    tooltip: "The customer has arrived.",
    action: "Assign table and seat guests",
  },
  SEATED: {
    label: "In service",
    tone: "border-emerald-300/35 bg-emerald-400/15 text-emerald-100",
    dot: "bg-emerald-300",
    tooltip: "Customer seated or in service.",
    action: "Mark as completed when service ends",
  },
  COMPLETED: {
    label: "Finished",
    tone: "border-slate-300/25 bg-slate-400/10 text-slate-100",
    dot: "bg-slate-300",
    tooltip: "Completed reservation.",
    action: "Release or clean table",
  },
  CANCELLED: {
    label: "Cancelled",
    tone: "border-rose-300/30 bg-rose-400/10 text-rose-100",
    dot: "bg-rose-300",
    tooltip: "Cancelled reservation.",
    action: "Does not occupy a table",
  },
  NO_SHOW: {
    label: "No show",
    tone: "border-fuchsia-300/30 bg-fuchsia-400/10 text-fuchsia-100",
    dot: "bg-fuchsia-300",
    tooltip: "Customer did not show.",
    action: "Registrar historial",
  },
  CONFLICT: {
    label: "Conflict",
    tone: "border-red-300/45 bg-red-500/15 text-red-100",
    dot: "bg-red-300",
    tooltip: "Overlap or invalid rule.",
    action: "Resolver conflicto",
  },
  UNASSIGNED: {
    label: "Unassigned",
    tone: "border-violet-300/35 bg-violet-400/15 text-violet-100",
    dot: "bg-violet-300",
    tooltip: "Reservation without an assigned table.",
    action: "Assign table",
  },
  CLEANING: {
    label: "Cleaning",
    tone: "border-cyan-300/35 bg-cyan-400/15 text-cyan-100",
    dot: "bg-cyan-300",
    tooltip: "Cleaning time between reservations.",
    action: "Prepare next reservation",
  },
};

const SERVICE_WINDOWS: Record<ServiceWindow, { label: string; start: number; end: number }> = {
  all: { label: "All day", start: 0, end: 24 * 60 },
  lunch: { label: "Lunch", start: 11 * 60, end: 17 * 60 },
  dinner: { label: "Dinner", start: 17 * 60, end: 24 * 60 },
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
      tooltip: "Inactive or blocked table.",
      action: "Review settings",
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

function StatCardCompact({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-white/10 bg-slate-950/45 px-3 py-2.5">
      <p className="text-[10px] font-semibold uppercase text-slate-500">{label}</p>
      <p className="mt-0.5 text-lg font-semibold text-white">{value}</p>
    </div>
  );
}

function StatusLegend() {
  const { t } = useI18n();
  const items: Array<ReservationStatus | "FREE" | "UNASSIGNED" | "CONFLICT" | "CLEANING"> = [
    "FREE",
    "CONFIRMED",
    "PENDING",
    "ARRIVED",
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
            title={t(visual.tooltip)}
            className={[
              "inline-flex h-9 items-center gap-2 rounded-full border px-3 text-xs font-semibold",
              visual.tone,
            ].join(" ")}
          >
            <span className={["h-2 w-2 rounded-full", visual.dot].join(" ")} />
            {t(visual.label)}
          </span>
        );
      })}
    </div>
  );
}

export function PlanningPage() {
  const { t } = useI18n();
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
  const [createModalOpen, setCreateModalOpen] = useState(false);
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

  const selectedReservation = useMemo(() => {
    if (!selectedReservationId || !planning) return null;
    return [...planning.assignedReservations, ...planning.unassignedReservations]
      .find((r) => r.reservationId === selectedReservationId) ?? null;
  }, [planning, selectedReservationId]);

  const fullReservationQuery = useQuery({
    queryKey: ["reservation-detail", activeRestaurantId, selectedReservationId],
    queryFn: () => frontdeskApi.getReservation(activeRestaurantId!, selectedReservationId!),
    enabled: selectedReservationId !== null && activeRestaurantId !== null,
    retry: 1,
  });

  const customerQuery = useQuery({
    queryKey: ["customer", activeRestaurantId, selectedReservation?.customerId],
    queryFn: () => frontdeskApi.getCustomer(activeRestaurantId!, selectedReservation!.customerId),
    enabled: (selectedReservation?.customerId ?? 0) > 0 && activeRestaurantId !== null,
    retry: 1,
  });

  const diningRoomName = useMemo(() => {
    if (!selectedReservation?.tableId || !planning) return null;
    for (const room of planning.diningRooms) {
      if (room.tables.some((t) => t.id === selectedReservation.tableId)) {
        return room.name;
      }
    }
    return null;
  }, [planning, selectedReservation]);

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
    <section className="grid min-w-0 gap-6">
      <header className="rounded-lg border border-white/8 bg-[#111614] p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold text-white sm:text-3xl">
              {t("Planning")}
            </h1>
            <p className="mt-1 text-sm text-slate-400">{selectedDate}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="h-11 min-w-[120px] rounded-lg bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 active:scale-[0.97]"
              onClick={() => setCreateModalOpen(true)}
            >
              <span className="inline-flex items-center gap-2">
                <Plus className="h-4 w-4" />
                {t("New reservation")}
              </span>
            </button>
            <button
              type="button"
              className="h-11 min-w-[100px] rounded-lg border border-white/10 bg-white/10 px-4 text-sm font-semibold text-white transition hover:border-brand-300/40 hover:bg-brand-500/10 active:scale-[0.97] disabled:opacity-60"
              disabled={recalculateMutation.isPending || activeRestaurantId === null}
              onClick={() => { void recalculateMutation.mutateAsync(); }}
            >
              <span className="inline-flex items-center gap-2">
                <RefreshCw className={recalculateMutation.isPending ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
                {recalculateMutation.isPending ? t("Optimizing...") : t("Optimize")}
              </span>
            </button>
            <button
              type="button"
              className="h-11 min-w-[100px] rounded-lg border border-white/10 bg-white/10 px-4 text-sm font-semibold text-white transition hover:border-brand-300/40 hover:bg-brand-500/10 active:scale-[0.97]"
              onClick={() => navigate("/settings/layout")}
            >
              <span className="inline-flex items-center gap-2">
                <Map className="h-4 w-4" />
                {t("Floor plan")}
              </span>
            </button>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
          <label className="grid gap-1 rounded-lg border border-white/10 bg-slate-950/40 p-3">
            <span className="text-[10px] font-semibold uppercase text-slate-500">{t("Date")}</span>
            <input
              className="h-10 rounded-lg border border-white/10 bg-slate-950/70 px-3 text-sm text-white outline-none focus:border-brand-300/70"
              type="date"
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </label>
          <label className="grid gap-1 rounded-lg border border-white/10 bg-slate-950/40 p-3">
            <span className="text-[10px] font-semibold uppercase text-slate-500">{t("Service")}</span>
            <select
              className="h-10 rounded-lg border border-white/10 bg-slate-950/70 px-3 text-sm text-white outline-none focus:border-brand-300/70"
              value={serviceWindow}
              onChange={(event) => setServiceWindow(event.target.value as ServiceWindow)}
            >
              {Object.entries(SERVICE_WINDOWS).map(([value, window]) => (
                <option key={value} value={value}>{t(window.label)}</option>
              ))}
            </select>
          </label>
          <label className="col-span-2 grid gap-1 rounded-lg border border-white/10 bg-slate-950/40 p-3">
            <span className="text-[10px] font-semibold uppercase text-slate-500">{t("Dining room")}</span>
            <select
              className="h-10 rounded-lg border border-white/10 bg-slate-950/70 px-3 text-sm text-white outline-none focus:border-brand-300/70"
              value={selectedRoomId}
              onChange={(event) =>
                setSelectedRoomId(event.target.value === "all" ? "all" : Number(event.target.value))
              }
            >
              <option value="all">{t("All dining rooms")}</option>
              {(planning?.diningRooms ?? []).map((room) => (
                <option key={room.id} value={room.id}>{room.name}</option>
              ))}
            </select>
          </label>
          <StatCardCompact label={t("Occupancy")} value={`${stats.occupancy}%`} />
          <StatCardCompact label={t("Reservations")} value={stats.reservations} />
          <StatCardCompact label={t("Pending")} value={stats.pending} />
        </div>
      </header>

      <StatusLegend />

      <InsightBar
        summary={aiSummaryQuery.data}
        insights={insights}
        onOpenPanel={() => setInsightPanelOpen(true)}
      />

      <NeedsAttentionPanel
        planning={planning}
        onSelectReservation={(reservationId) => {
          setSelectedReservationId(reservationId);
          setSelectedTableId(null);
          setLastAssignResponse(null);
        }}
      />

      {planningQuery.isLoading ? (
        <div className="flex items-center gap-3 rounded-lg border border-white/10 bg-white/5 px-6 py-5 text-slate-300">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-400 border-t-transparent" />
          <span className="text-sm">{t("Loading planning...")}</span>
        </div>
      ) : null}

      {planningQuery.error ? (
        <div className="rounded-lg border border-rose-300/20 bg-rose-500/8 px-6 py-5">
          <p className="text-sm font-medium text-rose-200">{t("Could not load planning")}</p>
          <p className="mt-1 text-sm text-rose-300/80">{getErrorMessage(planningQuery.error)}</p>
        </div>
      ) : null}

      {planning ? (
        <>
          <div className="grid min-w-0 gap-5 xl:grid-cols-[320px_minmax(0,1fr)_360px]">
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
            <section className="rounded-lg border border-red-300/25 bg-red-500/10 p-5">
              <h2 className="text-xl font-semibold text-red-100">{t("Conflicts")}</h2>
              <div className="mt-4 grid gap-3 md:grid-cols-2">
                {planning.conflicts.map((conflict, index) => (
                  <article key={`${conflict.resourceId}-${index}`} className="rounded-lg border border-red-300/20 bg-slate-950/40 p-4 text-red-100">
                    <p className="font-semibold">{conflict.resourceLabel}</p>
                    <p className="mt-2 text-sm">{conflict.message}</p>
                    <p className="mt-2 text-xs uppercase text-red-200/80">
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

      <ReservationCreateModal
        open={createModalOpen}
        restaurantId={activeRestaurantId!}
        selectedDate={selectedDate}
        onClose={() => setCreateModalOpen(false)}
        onCreated={(reservationId) => {
          setSelectedReservationId(reservationId);
          setSelectedTableId(null);
          setLastAssignResponse(null);
          setCreateModalOpen(false);
          queryClient.invalidateQueries({ queryKey: ["planning", activeRestaurantId, selectedDate] });
        }}
      />

      <ReservationSidePanel
        open={selectedReservationId !== null}
        reservationSummary={selectedReservation}
        fullReservation={fullReservationQuery.data}
        fullReservationLoading={fullReservationQuery.isLoading}
        customer={customerQuery.data}
        customerLoading={customerQuery.isLoading}
        diningRoomName={diningRoomName}
        restaurantId={activeRestaurantId!}
        selectedDate={selectedDate}
        canManageAssignments={canDismissInsights}
        onClose={() => {
          setSelectedReservationId(null);
          setSelectedTableId(null);
          setLastAssignResponse(null);
        }}
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
  const { t } = useI18n();
  const filters: StatusFilter[] = ["all", "CONFIRMED", "PENDING", "SEATED", "UNASSIGNED"];

  return (
    <aside className="rounded-lg border border-white/10 bg-slate-950/70 p-4 shadow-2xl shadow-black/20">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-brand-300">
            {t("Reservations")}
          </p>
          <h2 className="mt-2 text-xl font-semibold text-white">{t("Today's queue")}</h2>
        </div>
        <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold text-slate-300">
          {reservations.length}
        </span>
      </div>

      <input
        className="mt-4 h-12 w-full rounded-lg border border-white/10 bg-slate-900/80 px-4 text-sm text-white outline-none placeholder:text-slate-500 focus:border-brand-300/70"
        value={searchQuery}
        onChange={(event) => onSearchQueryChange(event.target.value)}
        placeholder={t("Search name, time or guests")}
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
            {filter === "all" ? t("All") : t(STATUS_VISUALS[filter].label)}
          </button>
        ))}
      </div>

      <div className="mt-4 grid max-h-[640px] gap-2 overflow-y-auto pr-1">
        {reservations.map((reservation) => {
          const isUnassigned = reservation.tableId === null && reservation.tableCombinationId === null;
          return (
            <button
              key={reservation.reservationId}
              type="button"
              className={[
                "rounded-lg border px-4 py-3 text-left transition active:scale-[0.98]",
                selectedReservationId === reservation.reservationId
                  ? "border-brand-300/70 bg-brand-500/15"
                  : "border-white/[0.06] bg-white/[0.03] hover:border-white/20 hover:bg-white/[0.06]",
              ].join(" ")}
              onClick={() => onSelectReservation(reservation.reservationId)}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-white">{reservationName(reservation)}</span>
                    <span className="text-xs text-slate-500">·</span>
                    <span className="text-xs font-medium text-slate-400">{reservation.partySize}</span>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {normalizeTimeForInput(reservation.startTime)}
                    {reservation.tableCode ? ` · ${reservation.tableCode}` : ""}
                    {isUnassigned ? ` · ${t("No table")}` : ""}
                  </p>
                </div>
                <StatusPill status={reservation.status} />
              </div>
            </button>
          );
        })}

        {reservations.length === 0 ? (
          <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-6 text-center">
            <p className="text-sm text-slate-500">{t("No reservations match these filters.")}</p>
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
  const { t } = useI18n();
  return (
    <main className="grid min-w-0 gap-5">
      {rooms.map((room) => (
        <section key={room.id} className="overflow-hidden rounded-lg border border-white/10 bg-[#111614]">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 px-5 py-4">
            <div>
              <h2 className="text-2xl font-semibold text-white">{room.name}</h2>
              <p className="mt-1 text-sm text-slate-400">
                {t("Priority")} {room.priority} · {room.accessible ? t("Accessible") : t("Not accessible")} · {room.tables.length} {t("tables")}
              </p>
            </div>
            <span className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs font-semibold uppercase text-slate-300">
              {t("Service")}
            </span>
          </div>

          <div className="relative min-h-[520px] overflow-hidden bg-[#0d1210] p-4 sm:p-6">
            <div className="absolute inset-0 opacity-30 [background-image:linear-gradient(rgba(255,255,255,0.05)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.05)_1px,transparent_1px)] [background-size:32px_32px]" />
            <div className="absolute left-8 top-8 rounded-full border border-white/10 bg-slate-950/60 px-3 py-1 text-xs font-semibold uppercase text-slate-400">
              {t("Floor plan")}
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
                    "absolute flex min-h-24 flex-col justify-between rounded-lg border p-3 text-left shadow-lg shadow-black/20 transition duration-200 hover:ring-2 hover:ring-white/30",
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
                      <p className="text-base font-black">{table.code}</p>
                      <p className="text-xs opacity-80">{table.minCapacity}-{table.maxCapacity} {t("guests")}</p>
                    </div>
                    <span className={["mt-1 h-2.5 w-2.5 rounded-full", visual.dot].join(" ")} />
                  </div>

                  {primaryReservation ? (
                    <span
                      role="button"
                      tabIndex={0}
                      className="mt-3 rounded-lg bg-slate-950/35 px-3 py-2 text-xs font-semibold backdrop-blur transition hover:bg-slate-950/55"
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
                    <span className="mt-3 rounded-lg bg-slate-950/30 px-3 py-2 text-xs font-semibold">
                      {t("Free")}
                    </span>
                  )}
                </button>
              );
            })}

            {room.tables.length === 0 ? (
              <div className="relative z-10 rounded-lg border border-white/10 bg-white/5 p-6 text-slate-300">
                {t("This dining room has no tables.")}
              </div>
            ) : null}
          </div>
        </section>
      ))}

      {rooms.length === 0 ? (
        <section className="rounded-lg border border-white/10 bg-white/5 p-8 text-slate-300">
          {t("No dining rooms match the selected filter.")}
        </section>
      ) : null}
    </main>
  );
}

function NoAssignmentCard({ response }: { response: AssignReservationResponse }) {
  const { t } = useI18n();
  return (
    <div className="rounded-lg border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-50">
      <p className="font-semibold text-white">{t("Cannot assign at the requested time")}</p>
      <p className="mt-2 text-rose-100/90">
        {t("No table or combination meets the requirements.")}
      </p>

      {response.reasons.length > 0 ? (
        <ul className="mt-3 grid gap-2">
          {response.reasons.map((reason) => (
            <li key={reason} className="rounded-lg border border-rose-200/15 bg-slate-950/35 px-3 py-2">
              {t(humanizeAssignmentReason(reason))}
            </li>
          ))}
        </ul>
      ) : null}

      <div className="mt-4 rounded-lg border border-brand-300/30 bg-brand-400/10 p-3 text-brand-50">
        <p className="text-xs font-semibold uppercase text-brand-200">
          {t("Next option")}
        </p>
        {response.recommendedStartTime ? (
          <>
            <p className="mt-2 text-2xl font-semibold text-white">
              {normalizeTimeForInput(response.recommendedStartTime)}
            </p>
            <p className="mt-1 text-sm text-brand-100">
              {t("A table is available at that time. Edit the reservation to change it.")}
            </p>
          </>
        ) : (
          <p className="mt-2 text-sm text-brand-100">
            {t("No other option was found for this date.")}
          </p>
        )}
      </div>
    </div>
  );
}

function humanizeAssignmentReason(reason: string) {
  return reason
    .replace(/candidate\(s\) rejected due to insufficient_capacity/g, "options rejected due to insufficient capacity")
    .replace(/candidate\(s\) rejected due to below_min_capacity/g, "options rejected due to minimum capacity")
    .replace(/candidate\(s\) rejected due to time_overlap/g, "options rejected due to another reservation")
    .replace(/candidate\(s\) rejected due to accessibility_mismatch/g, "options rejected due to accessibility")
    .replace(/candidate\(s\) rejected due to inactive_table/g, "options rejected because a table is inactive")
    .replace(/candidate\(s\) rejected due to inactive_dining_room/g, "options rejected because a dining room is inactive")
    .replace(/candidate\(s\) rejected due to inactive_combination/g, "options rejected because a combination is inactive")
    .replace("No active tables or combinations are configured for this restaurant", "No active tables or combinations are configured")
    .replace("No candidate satisfied the hard constraints", "No table or combination satisfies the required rules");
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
  const { t } = useI18n();
  return (
    <aside className="rounded-lg border border-white/10 bg-slate-950/70 p-5 shadow-2xl shadow-black/20">
      <p className="text-xs font-semibold uppercase text-brand-300">
        {t("Details")}
      </p>

      {assignError ? (
        <div className="mt-4 rounded-lg border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
          {getErrorMessage(assignError)}
        </div>
      ) : null}

      {selectedReservation ? (
        <div className="mt-4 grid gap-4">
          <div>
            <h2 className="text-3xl font-semibold text-white">{reservationName(selectedReservation)}</h2>
            <p className="mt-2 text-sm text-slate-400">
              {selectedReservation.partySize} {t("guests")} · {formatRange(selectedReservation)}
            </p>
          </div>
          <StatusPill status={selectedReservation.status} />
          <div className="grid gap-3 rounded-lg border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
            <p>{t("Table")}: {selectedReservation.tableCode ?? selectedReservation.tableCombinationName ?? t("Unassigned")}</p>
            <p>{t("Cleaning")}: {selectedReservation.cleaningBufferMin} min</p>
            <p>{t("Accessibility")}: {selectedReservation.accessibilityRequired ? t("Required") : t("Not specified")}</p>
            <p>{t("Notes")}: {selectedReservation.specialRequests || t("No notes")}</p>
          </div>
          {assignResponse && !assignResponse.assigned ? (
            <NoAssignmentCard response={assignResponse} />
          ) : null}
          {selectedReservation.tableId === null && selectedReservation.tableCombinationId === null ? (
            <button
              type="button"
              className="h-12 rounded-lg bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
              disabled={assignPending}
              onClick={() => onAssignAutomatically(selectedReservation.reservationId)}
            >
              {assignPending ? t("Assigning...") : t("Find best table")}
            </button>
          ) : null}
        </div>
      ) : selectedTable ? (
        <div className="mt-4 grid gap-4">
          <div>
            <h2 className="text-3xl font-semibold text-white">{tableName(selectedTable)}</h2>
            <p className="mt-2 text-sm text-slate-400">
              {t("Capacity")} {selectedTable.minCapacity}-{selectedTable.maxCapacity} {t("guests")}
            </p>
          </div>
          <div className="grid gap-3">
            {selectedTable.reservations.length > 0 ? (
              selectedTable.reservations.map((reservation) => (
                <div key={reservation.reservationId} className="rounded-lg border border-white/10 bg-white/5 p-4">
                  <p className="font-semibold text-white">{reservationName(reservation)}</p>
                  <p className="mt-1 text-sm text-slate-400">
                    {formatRange(reservation)} · {reservation.partySize} {t("guests")}
                  </p>
                  <div className="mt-3">
                    <StatusPill status={reservation.status} />
                  </div>
                </div>
              ))
            ) : (
              <div className="rounded-lg border border-emerald-300/25 bg-emerald-400/10 p-4 text-sm text-emerald-100">
                {t("Table available for this service.")}
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="mt-4 rounded-lg border border-white/10 bg-white/5 p-5 text-sm text-slate-300">
          {t("Select a table or reservation to view details.")}
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
  const { t } = useI18n();
  const tables = rooms.flatMap((room) => room.tables.map((table) => ({ ...table, roomName: room.name })));
  const hourMarks = Array.from({ length: 14 }, (_, index) => 11 + index);
  const totalMinutes = TIMELINE_END - TIMELINE_START;

  return (
    <section className="min-w-0 rounded-lg border border-white/10 bg-slate-950/70 p-5 shadow-2xl shadow-black/20">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-brand-300">
            {t("Schedule")}
          </p>
          <h2 className="mt-2 text-xl font-semibold text-white">{t("Table schedule")}</h2>
        </div>
      </div>

      <div className="mt-5 overflow-x-auto">
        <div className="min-w-[980px]">
          <div className="grid grid-cols-[180px_minmax(760px,1fr)] border-b border-white/10 pb-2">
            <div className="text-xs font-semibold uppercase text-slate-500">{t("Table")}</div>
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
                <div className="rounded-lg border border-white/10 bg-white/5 px-3 py-3">
                  <p className="text-sm font-semibold text-white">{table.code}</p>
                  <p className="mt-1 text-xs text-slate-500">{table.roomName}</p>
                </div>
                <div className="relative h-16 overflow-hidden rounded-lg border border-white/10 bg-white/[0.03]">
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
                            "absolute top-2 h-12 rounded-lg border px-3 text-left text-xs font-semibold shadow-lg transition hover:ring-2 hover:ring-white/70",
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
                              title={t("Cleaning")}
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
