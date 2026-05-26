import { useState } from "react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type { ReservationResponse } from "@/features/frontdesk/types";
import {
  formatReservationCustomerName,
  normalizeTimeForInput,
} from "@/features/frontdesk/utils/frontdeskUtils";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";

interface ReservationDetailPanelProps {
  reservation: ReservationResponse;
  canOperateReservations: boolean;
  onChanged: (reservation: ReservationResponse) => void;
}

export function ReservationDetailPanel({
  reservation,
  canOperateReservations,
  onChanged,
}: ReservationDetailPanelProps) {
  const { activeRestaurantId } = useActiveRestaurant();
  const [error, setError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<string | null>(null);

  async function runAction(
    action: "confirm" | "cancel" | "seat" | "complete" | "no-show",
  ) {
    if (!activeRestaurantId || !canOperateReservations) {
      return;
    }

    setPendingAction(action);
    setError(null);

    try {
      const nextReservation =
        action === "confirm"
          ? await frontdeskApi.confirmReservation(activeRestaurantId, reservation.id)
          : action === "cancel"
            ? await frontdeskApi.cancelReservation(activeRestaurantId, reservation.id)
            : action === "seat"
              ? await frontdeskApi.seatReservation(activeRestaurantId, reservation.id)
              : action === "complete"
                ? await frontdeskApi.completeReservation(activeRestaurantId, reservation.id)
                : await frontdeskApi.noShowReservation(activeRestaurantId, reservation.id);

      onChanged(nextReservation);
    } catch (actionError) {
      setError(getErrorMessage(actionError));
    } finally {
      setPendingAction(null);
    }
  }

  return (
    <div className="grid gap-5">
      {error ? <StatusMessage tone="error">{error}</StatusMessage> : null}

      <div className="rounded-3xl border border-white/10 bg-white/5 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
              Reserva #{reservation.id}
            </p>
            <h3 className="mt-2 text-2xl font-semibold text-white">
              {formatReservationCustomerName(reservation)}
            </h3>
            <p className="mt-2 text-sm text-slate-400">
              {reservation.partySize} pax · {reservation.reservationDate} ·{" "}
              {normalizeTimeForInput(reservation.startTime)}
              {reservation.endTime ? ` - ${normalizeTimeForInput(reservation.endTime)}` : ""}
            </p>
          </div>
          <StatusPill status={reservation.status} />
        </div>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-2xl border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase tracking-[0.2em] text-slate-500">Canal</dt>
          <dd className="mt-2 text-sm text-slate-200">{reservation.channel}</dd>
        </div>
        <div className="rounded-2xl border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase tracking-[0.2em] text-slate-500">Accesibilidad</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.accessibilityRequired ? "Si" : "No"}
          </dd>
        </div>
        <div className="rounded-2xl border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase tracking-[0.2em] text-slate-500">Duracion</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.estimatedDurationMin} min + {reservation.cleaningBufferMin} min limpieza
          </dd>
        </div>
        <div className="rounded-2xl border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase tracking-[0.2em] text-slate-500">Estado temporal</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.confirmedAt
              ? `Confirmada ${new Date(reservation.confirmedAt).toLocaleString()}`
              : reservation.cancelledAt
                ? `Cancelada ${new Date(reservation.cancelledAt).toLocaleString()}`
                : "Sin marca adicional"}
          </dd>
        </div>
      </dl>

      {reservation.specialRequests ? (
        <div className="rounded-3xl border border-white/10 bg-slate-950/55 p-5">
          <p className="text-xs uppercase tracking-[0.2em] text-slate-500">
            Peticiones especiales
          </p>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-200">
            {reservation.specialRequests}
          </p>
        </div>
      ) : null}

      <div className="grid gap-3">
        {!canOperateReservations ? (
          <StatusMessage tone="info">
            Tu rol actual puede consultar reservas, pero no operar cambios de estado.
          </StatusMessage>
        ) : null}

        <div className="flex flex-wrap gap-3">
          {reservation.status === "PENDING" ? (
            <button
              className="h-12 rounded-2xl bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("confirm");
              }}
            >
              {pendingAction === "confirm" ? "Confirmando..." : "Confirmar"}
            </button>
          ) : null}

          {reservation.status === "CONFIRMED" ? (
            <button
              className="h-12 rounded-2xl bg-emerald-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("seat");
              }}
            >
              {pendingAction === "seat" ? "Marcando..." : "Marcar sentado"}
            </button>
          ) : null}

          {reservation.status === "SEATED" ? (
            <button
              className="h-12 rounded-2xl bg-sky-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-sky-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("complete");
              }}
            >
              {pendingAction === "complete" ? "Cerrando..." : "Marcar completada"}
            </button>
          ) : null}

          {(reservation.status === "PENDING" || reservation.status === "CONFIRMED") ? (
            <button
              className="h-12 rounded-2xl border border-white/10 bg-rose-500/10 px-5 text-sm font-semibold text-rose-100 transition hover:border-rose-400/40 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("cancel");
              }}
            >
              {pendingAction === "cancel" ? "Cancelando..." : "Cancelar"}
            </button>
          ) : null}

          {(reservation.status === "PENDING" || reservation.status === "CONFIRMED") ? (
            <button
              className="h-12 rounded-2xl border border-white/10 bg-fuchsia-500/10 px-5 text-sm font-semibold text-fuchsia-100 transition hover:border-fuchsia-400/40 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("no-show");
              }}
            >
              {pendingAction === "no-show" ? "Guardando..." : "Marcar no-show"}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
