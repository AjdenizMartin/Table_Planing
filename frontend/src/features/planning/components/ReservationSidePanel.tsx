import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import * as planningApi from "@/features/planning/api/planningApi";
import type { ReservationResponse, CustomerResponse } from "@/features/frontdesk/types";
import type { PlanningReservationSummaryResponse } from "@/features/planning/types";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { notify } from "@/features/notifications/components/NotificationToast";
import { normalizeTimeForInput } from "@/features/frontdesk/utils/frontdeskUtils";
import { EditReservationModal } from "@/features/planning/components/EditReservationModal";
import { AssignmentSuggestionsPanel } from "@/features/planning/components/AssignmentSuggestionsPanel";

const CHANNEL_LABELS: Record<string, string> = {
  MANUAL: "Manual",
  PHONE: "Telefono",
  WEB: "Web",
  GOOGLE: "Google",
  INSTAGRAM: "Instagram",
  FACEBOOK: "Facebook",
  WHATSAPP: "WhatsApp",
};

type ActionKey = "confirm" | "cancel" | "arrived" | "seat" | "complete" | "no-show" | "send-confirmation" | "reminder" | "reassign" | "edit";

const SUCCESS_MESSAGES: Record<ActionKey, string> = {
  confirm: "Reservation confirmed.",
  cancel: "Reservation cancelled.",
  arrived: "Customer marked as arrived.",
  seat: "Customer marked as seated.",
  complete: "Reservation completed.",
  "no-show": "Customer marked as no-show.",
  "send-confirmation": "Confirmation sent.",
  reminder: "Reminder sent.",
  reassign: "Asignacion aplicada.",
  edit: "",
};

const ACTION_LABELS: Record<ActionKey, string> = {
  confirm: "Confirm",
  cancel: "Cancel",
  arrived: "Mark arrived",
  seat: "Mark seated",
  complete: "Mark finished",
  "no-show": "No-show",
  "send-confirmation": "Send confirmation",
  reminder: "Send reminder",
  reassign: "Ver sugerencias",
  edit: "Edit details",
};

interface Props {
  open: boolean;
  reservationSummary: PlanningReservationSummaryResponse | null;
  fullReservation: ReservationResponse | undefined;
  fullReservationLoading: boolean;
  customer: CustomerResponse | undefined;
  customerLoading: boolean;
  diningRoomName: string | null;
  restaurantId: number;
  selectedDate: string;
  canManageAssignments: boolean;
  onClose: () => void;
}

function getVisibleActions(status: string, reservation: PlanningReservationSummaryResponse): ActionKey[] {
  const actions: ActionKey[] = [];

  if (status === "PENDING") {
    actions.push("confirm", "cancel", "arrived", "seat", "no-show");
  } else if (status === "CONFIRMED") {
    actions.push("cancel", "arrived", "seat", "no-show");
  } else if (status === "ARRIVED") {
    actions.push("cancel", "seat", "no-show");
  } else if (status === "SEATED") {
    actions.push("complete");
  }

  if (status !== "COMPLETED" && status !== "CANCELLED" && status !== "NO_SHOW") {
    actions.push("send-confirmation", "reminder");
    actions.push("reassign");
    actions.push("edit");
  }

  return actions;
}

function formatName(reservation: PlanningReservationSummaryResponse, full: ReservationResponse | undefined) {
  if (full?.customerFirstName || full?.customerLastName) {
    return [full.customerFirstName, full.customerLastName].filter(Boolean).join(" ");
  }
  return reservation.customerName || `Reservation #${reservation.reservationId}`;
}

function formatPhone(customer: CustomerResponse | undefined, loading: boolean) {
  if (loading) return "Cargando...";
  if (customer?.phone) return customer.phone;
  return "No registrado";
}

function formatConfirmedAt(reservation: ReservationResponse | undefined) {
  if (!reservation) return null;
  if (reservation.confirmedAt) {
    return `Confirmada ${new Date(reservation.confirmedAt).toLocaleString()}`;
  }
  if (reservation.cancelledAt) {
    return `Cancelada ${new Date(reservation.cancelledAt).toLocaleString()}`;
  }
  return "Sin confirmar";
}

function OperationalMessage({ label, value }: { label: string; value: string | null }) {
  if (!value) return null;
  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-xs text-slate-300">
      <span className="font-semibold uppercase tracking-[0.15em] text-slate-500">{label}:</span>{" "}
      {value}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string | number | null | undefined }) {
  return (
    <div className="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-2.5">
      <p className="text-[10px] font-semibold uppercase tracking-[0.15em] text-slate-500">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-white">{value ?? "—"}</p>
    </div>
  );
}

export function ReservationSidePanel({
  open,
  reservationSummary,
  fullReservation,
  fullReservationLoading,
  customer,
  customerLoading,
  diningRoomName,
  restaurantId,
  selectedDate,
  canManageAssignments,
  onClose,
}: Props) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [activeAction, setActiveAction] = useState<ActionKey | null>(null);
  const [actionResult, setActionResult] = useState<string | null>(null);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onClose]);

  const actions = useMemo(
    () => {
      const visible = reservationSummary
        ? getVisibleActions(reservationSummary.status, reservationSummary)
        : [];
      return canManageAssignments ? visible : visible.filter((action) => action !== "reassign");
    },
    [canManageAssignments, reservationSummary],
  );

  useEffect(() => {
    setSuggestionsOpen(false);
    setError(null);
    setActionResult(null);
  }, [reservationSummary?.reservationId]);

  const suggestionsQuery = useQuery({
    queryKey: ["assignment-suggestions", restaurantId, reservationSummary?.reservationId],
    queryFn: () => planningApi.getAssignmentSuggestions(
      restaurantId,
      reservationSummary!.reservationId,
    ),
    enabled: open && suggestionsOpen && canManageAssignments && reservationSummary !== null,
    retry: 1,
  });

  const historyQuery = useQuery({
    queryKey: ["assignment-history", restaurantId, reservationSummary?.reservationId],
    queryFn: () => planningApi.getAssignmentHistory(
      restaurantId,
      reservationSummary!.reservationId,
    ),
    enabled: open && reservationSummary !== null,
    retry: 1,
  });

  const selectAssignmentMutation = useMutation({
    mutationFn: (candidate: { candidateType: "TABLE" | "TABLE_COMBINATION"; candidateId: number }) =>
      planningApi.selectAssignment(
        restaurantId,
        reservationSummary!.reservationId,
        candidate,
      ),
    onSuccess: async () => {
      setSuggestionsOpen(false);
      setError(null);
      setActionResult("Asignacion aplicada y recursos reservados.");
      notify("Asignacion aplicada.", "");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["planning", restaurantId, selectedDate] }),
        queryClient.invalidateQueries({
          queryKey: ["assignment-history", restaurantId, reservationSummary?.reservationId],
        }),
        queryClient.invalidateQueries({
          queryKey: ["assignment-suggestions", restaurantId, reservationSummary?.reservationId],
        }),
      ]);
    },
    onError: (err) => {
      setError(getErrorMessage(err));
      void suggestionsQuery.refetch();
    },
  });

  const actionMutation = useMutation({
    mutationFn: async ({ action }: { action: ActionKey }) => {
      if (!reservationSummary) throw new Error("No reservation selected");
      const id = reservationSummary.reservationId;

      switch (action) {
        case "confirm":
          return { type: "reservation" as const, data: await frontdeskApi.confirmReservation(restaurantId, id) };
        case "cancel":
          return { type: "reservation" as const, data: await frontdeskApi.cancelReservation(restaurantId, id) };
        case "arrived":
          return { type: "reservation" as const, data: await frontdeskApi.arrivedReservation(restaurantId, id) };
        case "seat":
          return { type: "reservation" as const, data: await frontdeskApi.seatReservation(restaurantId, id) };
        case "complete":
          return { type: "reservation" as const, data: await frontdeskApi.completeReservation(restaurantId, id) };
        case "no-show":
          return { type: "reservation" as const, data: await frontdeskApi.noShowReservation(restaurantId, id) };
        case "send-confirmation": {
          const result = await frontdeskApi.sendReservationConfirmation(restaurantId, id);
          if (result.status === "FAILED" || result.errorMessage) {
            throw new Error(result.errorMessage || "Failed to send confirmation");
          }
          return { type: "notification" as const };
        }
        case "reminder": {
          const result = await frontdeskApi.sendReservationReminder(restaurantId, id);
          if (result.status === "FAILED" || result.errorMessage) {
            throw new Error(result.errorMessage || "Failed to send reminder");
          }
          return { type: "notification" as const };
        }
        default:
          throw new Error(`Unknown action: ${action}`);
      }
    },
    onSuccess: (_data, { action }) => {
      setError(null);
      setActiveAction(null);
      notify(SUCCESS_MESSAGES[action] || "Action completed.", "");
      setActionResult(SUCCESS_MESSAGES[action] || "Action completed.");

      queryClient.invalidateQueries({ queryKey: ["planning", restaurantId, selectedDate] });
      if (reservationSummary) {
        queryClient.invalidateQueries({
          queryKey: ["reservation-detail", restaurantId, reservationSummary.reservationId],
        });
      }
    },
    onError: (err) => {
      setActiveAction(null);
      const message = getErrorMessage(err);
      setError(message);
      setActionResult(null);
    },
  });

  function handleAction(action: ActionKey) {
    if (action === "edit") {
      setEditModalOpen(true);
      return;
    }
    if (action === "reassign") {
      setSuggestionsOpen((current) => !current);
      setError(null);
      setActionResult(null);
      return;
    }
    if (activeAction) return;
    setError(null);
    setActionResult(null);
    setActiveAction(action);
    actionMutation.mutate({ action });
  }

  if (!open || !reservationSummary) return null;

  const isLoading = fullReservationLoading || customerLoading;

  function actionButtonClass(actionKey: ActionKey) {
    const base = "h-12 min-w-0 rounded-xl px-3 text-sm font-semibold transition active:scale-[0.97]";
    const disabled = activeAction !== null && activeAction !== actionKey;
    const loading = activeAction === actionKey;

    const tone = actionKey === "confirm"
      ? "bg-brand-500 text-slate-950 hover:bg-brand-400"
      : actionKey === "cancel"
        ? "border border-white/10 bg-rose-500/8 text-rose-200 hover:bg-rose-500/15"
        : actionKey === "arrived"
          ? "border border-white/10 bg-teal-500/8 text-teal-200 hover:bg-teal-500/15"
          : actionKey === "seat"
            ? "border border-white/10 bg-emerald-500/8 text-emerald-200 hover:bg-emerald-500/15"
            : actionKey === "complete"
              ? "border border-white/10 bg-sky-500/8 text-sky-200 hover:bg-sky-500/15"
              : actionKey === "no-show"
                ? "border border-white/10 bg-fuchsia-500/8 text-fuchsia-200 hover:bg-fuchsia-500/15"
                : actionKey === "send-confirmation" || actionKey === "reminder"
                  ? "border border-white/10 bg-cyan-500/8 text-cyan-200 hover:bg-cyan-500/15"
                  : actionKey === "reassign"
                    ? "border border-white/10 bg-violet-500/8 text-violet-200 hover:bg-violet-500/15"
                    : "border border-white/10 bg-white/[0.04] text-white hover:bg-white/10";

    return [base, tone, disabled ? "opacity-40 pointer-events-none" : "", loading ? "animate-pulse" : ""]
      .filter(Boolean).join(" ");
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-center bg-slate-950/55 backdrop-blur-sm lg:justify-end">
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
      <div className="hidden flex-1 lg:block" onClick={onClose} />

      <aside
        className="flex h-full w-full flex-col overflow-y-auto border-l border-white/10 bg-slate-950 shadow-2xl shadow-black/50 lg:max-w-xl"
        role="dialog"
        aria-label="Reservation details"
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-white/10 px-5 pb-4 pt-5">
          <div className="min-w-0">
            <p className="truncate text-xs font-semibold uppercase tracking-[0.25em] text-brand-300">
              Reserva #{reservationSummary.reservationId}
            </p>
            <h2 className="mt-1 truncate text-2xl font-semibold text-white">
              {isLoading ? "Cargando..." : formatName(reservationSummary, fullReservation)}
            </h2>
          </div>
          <button
            type="button"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-white/5 text-lg text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
            onClick={onClose}
            aria-label="Close panel"
          >
            ✕
          </button>
        </div>

        {isLoading ? (
          <div className="flex flex-1 items-center justify-center p-8">
            <div className="text-sm text-slate-400">Cargando detalle de la reserva...</div>
          </div>
        ) : (
          <div className="flex-1 space-y-5 px-5 py-5">
            {/* Status */}
            <StatusPill status={reservationSummary.status} />

            {/* Error */}
            {error ? (
              <div className="rounded-3xl border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
                {error}
              </div>
            ) : null}

            {/* Success */}
            {actionResult ? (
              <div className="rounded-3xl border border-emerald-300/30 bg-emerald-500/10 p-4 text-sm text-emerald-100">
                {actionResult}
              </div>
            ) : null}

            {/* Info grid */}
            <div className="grid grid-cols-2 gap-3">
              <InfoRow label="Telefono" value={formatPhone(customer, customerLoading)} />
              <InfoRow label="Comensales" value={reservationSummary.partySize} />
              <InfoRow label="Fecha" value={reservationSummary.reservationDate} />
              <InfoRow label="Hora" value={normalizeTimeForInput(reservationSummary.startTime)} />
              <InfoRow label="Duracion" value={`${reservationSummary.estimatedDurationMin} min`} />
              <InfoRow
                label="Mesa"
                value={reservationSummary.tableCode ?? reservationSummary.tableCombinationName ?? "Sin asignar"}
              />
              <InfoRow label="Salon" value={diningRoomName} />
              <InfoRow
                label="Canal"
                value={fullReservation ? (CHANNEL_LABELS[fullReservation.channel] ?? fullReservation.channel) : "—"}
              />
            </div>

            {/* Confirmation status */}
            {fullReservation ? (
              <div className="rounded-3xl border border-white/10 bg-slate-950/55 p-4">
                <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-500">
                  Estado de confirmacion
                </p>
                <p className="mt-1 text-sm text-slate-200">
                  {formatConfirmedAt(fullReservation) ?? "Sin confirmar"}
                </p>
              </div>
            ) : null}

            {/* Notes & preferences (merged) */}
            {reservationSummary.specialRequests ? (
              <div className="rounded-2xl border border-white/[0.06] bg-white/[0.02] px-4 py-3">
                <p className="text-[10px] font-semibold uppercase tracking-[0.15em] text-slate-500">Requests</p>
                <p className="mt-1.5 whitespace-pre-wrap text-sm leading-6 text-slate-200">
                  {reservationSummary.specialRequests}
                </p>
              </div>
            ) : null}

            {/* Accessibility badge */}
            {reservationSummary.accessibilityRequired ? (
              <div className="flex items-center gap-2 rounded-2xl border border-sky-300/20 bg-sky-500/10 px-4 py-2.5 text-sm text-sky-100">
                Accessibility required
              </div>
            ) : null}

            {/* Operational messages */}
            <div className="grid gap-2">
              <OperationalMessage
                label="Buffer limpieza"
                value={reservationSummary.cleaningBufferMin ? `${reservationSummary.cleaningBufferMin} min` : null}
              />
              <OperationalMessage
                label="Tipo de asignacion"
                value={reservationSummary.assignmentType || (reservationSummary.tableId ? "manual" : null)}
              />
              {reservationSummary.setupTimeMinutes > 0 ? (
                <OperationalMessage
                  label="Preparacion"
                  value={`${reservationSummary.setupTimeMinutes} min · coste ${costLabel(reservationSummary.operationalCostLevel)}`}
                />
              ) : null}
              {reservationSummary.assignedResources.length > 0 ? (
                <div className="rounded-2xl border border-amber-300/20 bg-amber-500/10 px-3 py-3 text-xs text-amber-50">
                  <p className="font-semibold uppercase tracking-[0.15em] text-amber-200">Inventario reservado</p>
                  <div className="mt-2 grid gap-1.5">
                    {reservationSummary.assignedResources.map((resource) => (
                      <p key={resource.storageResourceId}>
                        {resource.quantity} x {resource.resourceName}
                      </p>
                    ))}
                  </div>
                </div>
              ) : null}
              {reservationSummary.tableId === null && reservationSummary.tableCombinationId === null ? (
                <div className="rounded-2xl border border-violet-300/20 bg-violet-500/10 px-3 py-2 text-xs text-violet-100">
                  <span className="font-semibold uppercase tracking-[0.15em] text-violet-200">Sin asignar:</span>{" "}
                  Esta reserva no tiene mesa. Usa "Reassign table".
                </div>
              ) : null}
            </div>

            {suggestionsOpen ? (
              <AssignmentSuggestionsPanel
                suggestions={suggestionsQuery.data?.suggestions ?? []}
                loading={suggestionsQuery.isLoading}
                errorMessage={suggestionsQuery.error ? getErrorMessage(suggestionsQuery.error) : null}
                selecting={selectAssignmentMutation.isPending}
                onRefresh={() => void suggestionsQuery.refetch()}
                onSelect={(candidate) => selectAssignmentMutation.mutate(candidate)}
              />
            ) : null}

            {historyQuery.data && historyQuery.data.length > 0 ? (
              <section className="border-b border-white/10 pb-4">
                <h3 className="text-sm font-semibold text-white">Historial de asignacion</h3>
                <div className="mt-3 grid gap-2">
                  {historyQuery.data.slice(0, 4).map((item) => (
                    <div key={item.assignmentId} className="flex items-start justify-between gap-3 text-xs">
                      <div>
                        <p className="text-slate-200">
                          {item.tableCode ?? item.tableCombinationName ?? item.assignmentType}
                          {item.active ? " · actual" : ""}
                        </p>
                        <p className="mt-0.5 text-slate-500">
                          {item.assignedByName ?? "Sistema"} · {formatDateTime(item.assignedAt)}
                        </p>
                      </div>
                      {item.resources.length > 0 ? (
                        <span className="shrink-0 text-amber-200">{item.resources.length} recursos</span>
                      ) : null}
                    </div>
                  ))}
                </div>
              </section>
            ) : null}

            {/* Hour protection notice */}
            <div className="rounded-3xl border border-amber-300/25 bg-amber-400/10 p-3 text-xs text-amber-100">
              La hora de esta reserva esta protegida. No se puede cambiar desde este panel.
            </div>

            {/* Actions */}
            <div className="grid grid-cols-2 gap-3 pb-4">
              {actions.map((actionKey) => {
                const isLoading = activeAction === actionKey;

                return (
                  <button
                    key={actionKey}
                    type="button"
                    disabled={activeAction !== null && !isLoading}
                    className={actionButtonClass(actionKey)}
                    onClick={() => handleAction(actionKey)}
                  >
                    {isLoading ? "Procesando..." : ACTION_LABELS[actionKey]}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </aside>

      <EditReservationModal
        open={editModalOpen}
        reservationId={reservationSummary.reservationId}
        customerId={fullReservation?.customerId ?? reservationSummary.customerId}
        fullReservation={fullReservation}
        customer={customer}
        reservationSummary={reservationSummary}
        restaurantId={restaurantId}
        selectedDate={selectedDate}
        onClose={() => setEditModalOpen(false)}
      />
    </div>
  );
}

function costLabel(level: "LOW" | "MEDIUM" | "HIGH" | null) {
  if (!level) return "bajo";
  return level === "LOW" ? "bajo" : level === "MEDIUM" ? "medio" : "alto";
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : "sin fecha";
}
