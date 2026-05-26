import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { InsightBar } from "@/features/ai/components/InsightBar";
import { InsightPanel } from "@/features/ai/components/InsightPanel";
import {
  useAiInsights,
  useAiInsightsSummary,
  useDismissAiInsight,
} from "@/features/ai/hooks/useAiInsights";
import { useAuth } from "@/features/auth/context/AuthContext";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { SelectField, TextAreaField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { normalizeTimeForInput, todayDateValue } from "@/features/frontdesk/utils/frontdeskUtils";
import * as planningApi from "@/features/planning/api/planningApi";
import { PlanningGrid } from "@/features/planning/components/PlanningGrid";
import type {
  AssignReservationResponse,
  PlanningReservationSummaryResponse,
} from "@/features/planning/types";

function prettyExplanation(explanationJson: string | null) {
  if (!explanationJson) {
    return null;
  }

  try {
    return JSON.stringify(JSON.parse(explanationJson), null, 2);
  } catch {
    return explanationJson;
  }
}

function reservationDisplayName(reservation: PlanningReservationSummaryResponse | null) {
  if (!reservation) {
    return "Selecciona una reserva";
  }

  return reservation.customerName || `Reserva #${reservation.reservationId}`;
}

export function PlanningPage() {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedDate, setSelectedDate] = useState(todayDateValue());
  const [selectedRoomFilter, setSelectedRoomFilter] = useState<"all" | number>("all");
  const [selectedReservationId, setSelectedReservationId] = useState<number | null>(null);
  const [moveType, setMoveType] = useState<"table" | "combination">("table");
  const [moveResourceId, setMoveResourceId] = useState<string>("");
  const [assignmentFeedback, setAssignmentFeedback] = useState<AssignReservationResponse | null>(null);
  const [insightPanelOpen, setInsightPanelOpen] = useState(false);

  const activeRoles =
    session.restaurants.find((restaurant) => restaurant.id === activeRestaurantId)?.roles ?? [];
  const canOperatePlanning = activeRoles.some((role) =>
    ["PLATFORM_ADMIN", "RESTAURANT_OWNER", "MANAGER", "WAITER"].includes(role),
  );
  const canDismissInsights = activeRoles.some((role) =>
    ["PLATFORM_ADMIN", "RESTAURANT_OWNER", "MANAGER"].includes(role),
  );

  const aiInsightsQuery = useAiInsights(selectedDate);
  const aiSummaryQuery = useAiInsightsSummary(selectedDate);
  const dismissInsightMutation = useDismissAiInsight(selectedDate);

  const planningQuery = useQuery({
    queryKey: ["planning", activeRestaurantId, selectedDate],
    queryFn: () => planningApi.getPlanning(activeRestaurantId!, selectedDate),
    enabled: activeRestaurantId !== null,
  });

  const tablesQuery = useQuery({
    queryKey: ["tables", activeRestaurantId],
    queryFn: () => configApi.getTables(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  const combinationsQuery = useQuery({
    queryKey: ["tableCombinations", activeRestaurantId],
    queryFn: () => configApi.getTableCombinations(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  const recalculateMutation = useMutation({
    mutationFn: () => planningApi.recalculatePlanning(activeRestaurantId!, selectedDate),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["planning", activeRestaurantId, selectedDate],
      });
    },
  });

  const moveMutation = useMutation({
    mutationFn: () =>
      planningApi.moveReservation(activeRestaurantId!, {
        reservationId: selectedReservationId!,
        tableId: moveType === "table" ? Number(moveResourceId) : null,
        tableCombinationId: moveType === "combination" ? Number(moveResourceId) : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["planning", activeRestaurantId, selectedDate],
      });
      setAssignmentFeedback(null);
    },
  });

  const assignMutation = useMutation({
    mutationFn: (reservationId: number) =>
      planningApi.assignReservationAutomatically(activeRestaurantId!, reservationId),
    onSuccess: async (response) => {
      setAssignmentFeedback(response);
      await queryClient.invalidateQueries({
        queryKey: ["planning", activeRestaurantId, selectedDate],
      });
    },
  });

  const filteredDiningRooms = useMemo(() => {
    const diningRooms = planningQuery.data?.diningRooms ?? [];
    if (selectedRoomFilter === "all") {
      return diningRooms;
    }

    return diningRooms.filter((room) => room.id === selectedRoomFilter);
  }, [planningQuery.data?.diningRooms, selectedRoomFilter]);

  const allReservations = useMemo(() => {
    if (!planningQuery.data) {
      return [];
    }

    return [
      ...planningQuery.data.assignedReservations,
      ...planningQuery.data.unassignedReservations,
    ];
  }, [planningQuery.data]);

  const selectedReservation =
    allReservations.find((reservation) => reservation.reservationId === selectedReservationId) ??
    null;

  const availableTableOptions = useMemo(() => {
    const roomId = selectedRoomFilter === "all" ? null : selectedRoomFilter;
    return (tablesQuery.data ?? []).filter(
      (table) => table.active && (roomId === null || table.diningRoomId === roomId),
    );
  }, [selectedRoomFilter, tablesQuery.data]);

  const availableCombinationOptions = useMemo(
    () => (combinationsQuery.data ?? []).filter((combination) => combination.active),
    [combinationsQuery.data],
  );

  useEffect(() => {
    const roomIds = new Set((planningQuery.data?.diningRooms ?? []).map((room) => room.id));
    if (selectedRoomFilter !== "all" && !roomIds.has(selectedRoomFilter)) {
      setSelectedRoomFilter("all");
    }
  }, [planningQuery.data?.diningRooms, selectedRoomFilter]);

  useEffect(() => {
    if (!selectedReservationId && allReservations.length > 0) {
      setSelectedReservationId(allReservations[0].reservationId);
      return;
    }

    if (
      selectedReservationId !== null &&
      allReservations.every((reservation) => reservation.reservationId !== selectedReservationId)
    ) {
      setSelectedReservationId(null);
    }
  }, [allReservations, selectedReservationId]);

  useEffect(() => {
    setMoveResourceId("");
  }, [moveType, selectedReservationId, selectedRoomFilter]);

  return (
    <>
      <section className="grid gap-6">
      <header className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-2xl shadow-black/20 sm:p-8">
        <p className="text-xs uppercase tracking-[0.3em] text-brand-300">Planning Diario</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
          Vista operativa por mesa y hora
        </h1>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300 sm:text-base">
          El backend sigue siendo la fuente de verdad. Esta pantalla consume el planning diario,
          muestra reservas sin asignar y permite asignar o mover recursos recargando siempre desde servidor.
        </p>
      </header>

      <InsightBar
        summary={aiSummaryQuery.data}
        insights={aiInsightsQuery.data ?? []}
        onOpenPanel={() => setInsightPanelOpen(true)}
      />

      {aiInsightsQuery.error ? (
        <StatusMessage tone="error">{getErrorMessage(aiInsightsQuery.error)}</StatusMessage>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <div className="grid gap-6">
          <ConfigCard title="Controles del servicio">
            <div className="grid gap-4 lg:grid-cols-[220px_auto_auto]">
              <TextField
                label="Fecha"
                type="date"
                value={selectedDate}
                onChange={(event) => setSelectedDate(event.target.value)}
              />
              <div className="grid gap-2">
                <span className="text-sm font-medium text-slate-200">Filtro por salon</span>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className={[
                      "h-12 rounded-2xl px-4 text-sm font-medium transition",
                      selectedRoomFilter === "all"
                        ? "bg-brand-500 text-slate-950"
                        : "border border-white/10 bg-white/5 text-slate-200 hover:border-brand-400/40 hover:bg-brand-500/10",
                    ].join(" ")}
                    onClick={() => setSelectedRoomFilter("all")}
                  >
                    Todos
                  </button>
                  {(planningQuery.data?.diningRooms ?? []).map((room) => (
                    <button
                      key={room.id}
                      type="button"
                      className={[
                        "h-12 rounded-2xl px-4 text-sm font-medium transition",
                        selectedRoomFilter === room.id
                          ? "bg-brand-500 text-slate-950"
                          : "border border-white/10 bg-white/5 text-slate-200 hover:border-brand-400/40 hover:bg-brand-500/10",
                      ].join(" ")}
                      onClick={() => setSelectedRoomFilter(room.id)}
                    >
                      {room.name}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex items-end justify-start lg:justify-end">
                <button
                  className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                  type="button"
                  disabled={!canOperatePlanning || recalculateMutation.isPending}
                  onClick={() => {
                    void recalculateMutation.mutateAsync();
                  }}
                >
                  {recalculateMutation.isPending ? "Recalculando..." : "Recalcular planning"}
                </button>
              </div>
            </div>
          </ConfigCard>

          {planningQuery.isLoading ? (
            <StatusMessage tone="info">Cargando planning diario...</StatusMessage>
          ) : null}
          {planningQuery.error ? (
            <StatusMessage tone="error">{getErrorMessage(planningQuery.error)}</StatusMessage>
          ) : null}

          {planningQuery.data ? (
            <PlanningGrid
              diningRooms={filteredDiningRooms}
              timeBlocks={planningQuery.data.timeBlocks}
              selectedReservationId={selectedReservationId}
              onSelectReservation={setSelectedReservationId}
            />
          ) : null}
        </div>

        <div className="grid gap-6">
          <ConfigCard title="Reservas sin asignar">
            <div className="grid gap-3">
              {(planningQuery.data?.unassignedReservations ?? []).map((reservation) => (
                <article
                  key={reservation.reservationId}
                  className="rounded-3xl border border-white/10 bg-white/5 p-4"
                >
                  <div className="flex flex-col gap-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <h3 className="text-lg font-semibold text-white">
                          {reservation.customerName || `Reserva #${reservation.reservationId}`}
                        </h3>
                        <p className="mt-1 text-sm text-slate-400">
                          {normalizeTimeForInput(reservation.startTime)} · {reservation.partySize} pax
                        </p>
                      </div>
                      <StatusPill status={reservation.status} />
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="h-11 rounded-2xl border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                        onClick={() => setSelectedReservationId(reservation.reservationId)}
                      >
                        Seleccionar
                      </button>
                      <button
                        type="button"
                        className="h-11 rounded-2xl bg-brand-500 px-4 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                        disabled={!canOperatePlanning || assignMutation.isPending}
                        onClick={() => {
                          setSelectedReservationId(reservation.reservationId);
                          void assignMutation.mutateAsync(reservation.reservationId);
                        }}
                      >
                        {assignMutation.isPending &&
                        selectedReservationId === reservation.reservationId
                          ? "Asignando..."
                          : "Asignar automaticamente"}
                      </button>
                    </div>
                  </div>
                </article>
              ))}

              {(planningQuery.data?.unassignedReservations ?? []).length === 0 ? (
                <StatusMessage tone="info">
                  No hay reservas sin asignar para la fecha seleccionada.
                </StatusMessage>
              ) : null}
            </div>
          </ConfigCard>

          <ConfigCard
            title="Detalle y movimientos"
            subtitle="Selecciona una reserva del planning o de la lista de pendientes para operar sobre ella."
          >
            {moveMutation.error ? (
              <StatusMessage tone="error">{getErrorMessage(moveMutation.error)}</StatusMessage>
            ) : null}
            {assignMutation.error ? (
              <StatusMessage tone="error">{getErrorMessage(assignMutation.error)}</StatusMessage>
            ) : null}

            {selectedReservation ? (
              <div className="grid gap-5">
                <div className="rounded-3xl border border-white/10 bg-white/5 p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
                        Reserva #{selectedReservation.reservationId}
                      </p>
                      <h3 className="mt-2 text-2xl font-semibold text-white">
                        {reservationDisplayName(selectedReservation)}
                      </h3>
                      <p className="mt-2 text-sm text-slate-400">
                        {selectedReservation.partySize} pax · {selectedReservation.reservationDate} ·{" "}
                        {normalizeTimeForInput(selectedReservation.startTime)}
                        {selectedReservation.effectiveEndTime
                          ? ` - ${normalizeTimeForInput(selectedReservation.effectiveEndTime)}`
                          : ""}
                      </p>
                    </div>
                    <StatusPill status={selectedReservation.status} />
                  </div>

                  <div className="mt-4 flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="h-11 rounded-2xl bg-brand-500 px-4 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                      disabled={!canOperatePlanning || assignMutation.isPending}
                      onClick={() => {
                        void assignMutation.mutateAsync(selectedReservation.reservationId);
                      }}
                    >
                      {assignMutation.isPending ? "Asignando..." : "Asignar automaticamente"}
                    </button>
                  </div>
                </div>

                <div className="grid gap-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <SelectField
                      label="Tipo de movimiento"
                      value={moveType}
                      onChange={(event) =>
                        setMoveType(event.target.value as "table" | "combination")
                      }
                    >
                      <option value="table">Mesa</option>
                      <option value="combination">Combinacion</option>
                    </SelectField>

                    <SelectField
                      label={moveType === "table" ? "Mesa destino" : "Combinacion destino"}
                      value={moveResourceId}
                      onChange={(event) => setMoveResourceId(event.target.value)}
                    >
                      <option value="" disabled>
                        {moveType === "table"
                          ? "Selecciona una mesa"
                          : "Selecciona una combinacion"}
                      </option>
                      {moveType === "table"
                        ? availableTableOptions.map((table) => (
                            <option key={table.id} value={table.id}>
                              {table.code}
                              {table.label ? ` · ${table.label}` : ""}
                            </option>
                          ))
                        : availableCombinationOptions.map((combination) => (
                            <option key={combination.id} value={combination.id}>
                              {combination.name}
                            </option>
                          ))}
                    </SelectField>
                  </div>

                  <button
                    type="button"
                    className="h-12 rounded-2xl border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10 disabled:opacity-60"
                    disabled={!canOperatePlanning || !moveResourceId || moveMutation.isPending}
                    onClick={() => {
                      void moveMutation.mutateAsync();
                    }}
                  >
                    {moveMutation.isPending ? "Moviendo..." : "Mover reserva manualmente"}
                  </button>
                </div>

                {assignmentFeedback && assignmentFeedback.reservationId === selectedReservation.reservationId ? (
                  <div className="grid gap-4 rounded-3xl border border-white/10 bg-slate-950/55 p-5">
                    <div>
                      <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
                        Explicacion de asignacion
                      </p>
                      <p className="mt-2 text-sm text-slate-200">
                        {assignmentFeedback.summary || "El backend devolvio una asignacion sin resumen adicional."}
                      </p>
                    </div>

                    {assignmentFeedback.reasons.length > 0 ? (
                      <div>
                        <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
                          Motivos
                        </p>
                        <ul className="mt-2 grid gap-2 text-sm text-slate-200">
                          {assignmentFeedback.reasons.map((reason) => (
                            <li key={reason} className="rounded-2xl border border-white/10 bg-white/5 px-3 py-2">
                              {reason}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : null}

                    {assignmentFeedback.explanationJson ? (
                      <TextAreaField
                        label="Detalle tecnico"
                        readOnly
                        value={prettyExplanation(assignmentFeedback.explanationJson) ?? ""}
                        className="min-h-40 font-mono text-xs"
                      />
                    ) : null}
                  </div>
                ) : null}
              </div>
            ) : (
              <StatusMessage tone="info">
                Selecciona una reserva para ver su detalle, asignarla automaticamente o moverla manualmente.
              </StatusMessage>
            )}
          </ConfigCard>

          {planningQuery.data?.conflicts.length ? (
            <ConfigCard title="Conflictos detectados">
              <div className="grid gap-3">
                {planningQuery.data.conflicts.map((conflict, index) => (
                  <article
                    key={`${conflict.resourceType}-${conflict.resourceId}-${index}`}
                    className="rounded-3xl border border-rose-400/25 bg-rose-500/10 p-4"
                  >
                    <p className="text-sm font-semibold text-rose-100">
                      {conflict.resourceLabel}
                    </p>
                    <p className="mt-2 text-sm text-rose-100/90">{conflict.message}</p>
                    <p className="mt-1 text-xs uppercase tracking-[0.2em] text-rose-200/80">
                      {normalizeTimeForInput(conflict.overlappingStart)} -{" "}
                      {normalizeTimeForInput(conflict.overlappingEnd)}
                    </p>
                  </article>
                ))}
              </div>
            </ConfigCard>
          ) : null}
        </div>
      </div>
      </section>
      <InsightPanel
        insights={aiInsightsQuery.data ?? []}
        open={insightPanelOpen}
        onClose={() => setInsightPanelOpen(false)}
        onDismiss={(insightId) => {
          if (!canDismissInsights) {
            return;
          }
          void dismissInsightMutation.mutateAsync(insightId);
        }}
        dismissingInsightId={dismissInsightMutation.variables ?? null}
        canDismiss={canDismissInsights}
      />
    </>
  );
}
