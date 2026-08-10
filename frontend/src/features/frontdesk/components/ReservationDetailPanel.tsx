import { useState } from "react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type { ReservationResponse } from "@/features/frontdesk/types";
import { ApiError } from "@/services/api/client";
import {
  formatReservationCustomerName,
  normalizeTimeForInput,
} from "@/features/frontdesk/utils/frontdeskUtils";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { useI18n } from "@/features/i18n/I18nProvider";

interface AvailabilityConflictDetails {
  reservationId?: number;
  reasons?: string[];
  recommendedStartTime?: string | null;
  recommendationSummary?: string | null;
}

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
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const [error, setError] = useState<string | null>(null);
  const [availabilityConflict, setAvailabilityConflict] =
    useState<AvailabilityConflictDetails | null>(null);
  const [pendingAction, setPendingAction] = useState<string | null>(null);

  async function runAction(
    action: "confirm" | "cancel" | "seat" | "complete" | "no-show",
  ) {
    if (!activeRestaurantId || !canOperateReservations) {
      return;
    }

    setPendingAction(action);
    setError(null);
    setAvailabilityConflict(null);

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
      setAvailabilityConflict(extractAvailabilityConflict(actionError));
    } finally {
      setPendingAction(null);
    }
  }

  return (
    <div className="grid gap-5">
      {error ? <StatusMessage tone="error">{error}</StatusMessage> : null}
      {availabilityConflict ? (
        <AvailabilityConflictCard details={availabilityConflict} />
      ) : null}

      <div className="rounded-lg border border-white/10 bg-white/5 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase text-slate-500">
              {t("Reservation")} #{reservation.id}
            </p>
            <h3 className="mt-2 text-2xl font-semibold text-white">
              {formatReservationCustomerName(reservation)}
            </h3>
            <p className="mt-2 text-sm text-slate-400">
              {reservation.partySize} {t("guests")} · {reservation.reservationDate} ·{" "}
              {normalizeTimeForInput(reservation.startTime)}
              {reservation.endTime ? ` - ${normalizeTimeForInput(reservation.endTime)}` : ""}
            </p>
          </div>
          <StatusPill status={reservation.status} />
        </div>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase text-slate-500">{t("Channel")}</dt>
          <dd className="mt-2 text-sm text-slate-200">{reservation.channel}</dd>
        </div>
        <div className="rounded-lg border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase text-slate-500">{t("Accessibility")}</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.accessibilityRequired ? t("Yes") : t("No")}
          </dd>
        </div>
        <div className="rounded-lg border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase text-slate-500">{t("Duration")}</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.estimatedDurationMin} min + {reservation.cleaningBufferMin} min {t("cleaning")}
          </dd>
        </div>
        <div className="rounded-lg border border-white/10 bg-slate-950/55 p-4">
          <dt className="text-xs uppercase text-slate-500">{t("Status")}</dt>
          <dd className="mt-2 text-sm text-slate-200">
            {reservation.confirmedAt
              ? `${t("Confirmed")} ${new Date(reservation.confirmedAt).toLocaleString()}`
              : reservation.cancelledAt
                ? `${t("Cancelled")} ${new Date(reservation.cancelledAt).toLocaleString()}`
                : t("No activity recorded")}
          </dd>
        </div>
      </dl>

      {reservation.specialRequests ? (
        <div className="rounded-lg border border-white/10 bg-slate-950/55 p-5">
          <p className="text-xs uppercase text-slate-500">
            {t("Special requests")}
          </p>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-200">
            {reservation.specialRequests}
          </p>
        </div>
      ) : null}

      <div className="grid gap-3">
        {!canOperateReservations ? (
          <StatusMessage tone="info">
            {t("Your role can view reservations, but cannot change their status.")}
          </StatusMessage>
        ) : null}

        <div className="flex flex-wrap gap-3">
          {reservation.status === "PENDING" ? (
            <button
              className="h-12 rounded-lg bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("confirm");
              }}
            >
              {pendingAction === "confirm" ? t("Confirming...") : t("Confirm")}
            </button>
          ) : null}

          {reservation.status === "CONFIRMED" ? (
            <button
              className="h-12 rounded-lg bg-emerald-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("seat");
              }}
            >
              {pendingAction === "seat" ? t("Saving...") : t("Seat")}
            </button>
          ) : null}

          {reservation.status === "SEATED" ? (
            <button
              className="h-12 rounded-lg bg-sky-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-sky-400 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("complete");
              }}
            >
              {pendingAction === "complete" ? t("Saving...") : t("Complete")}
            </button>
          ) : null}

          {(reservation.status === "PENDING" || reservation.status === "CONFIRMED") ? (
            <button
              className="h-12 rounded-lg border border-white/10 bg-rose-500/10 px-5 text-sm font-semibold text-rose-100 transition hover:border-rose-400/40 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("cancel");
              }}
            >
              {pendingAction === "cancel" ? t("Cancelling...") : t("Cancel")}
            </button>
          ) : null}

          {(reservation.status === "PENDING" || reservation.status === "CONFIRMED") ? (
            <button
              className="h-12 rounded-lg border border-white/10 bg-fuchsia-500/10 px-5 text-sm font-semibold text-fuchsia-100 transition hover:border-fuchsia-400/40 disabled:opacity-60"
              type="button"
              disabled={!canOperateReservations || pendingAction !== null}
              onClick={() => {
                void runAction("no-show");
              }}
            >
              {pendingAction === "no-show" ? t("Saving...") : t("No show")}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function extractAvailabilityConflict(error: unknown): AvailabilityConflictDetails | null {
  if (!(error instanceof ApiError)) {
    return null;
  }

  const payload = error.details as { details?: unknown } | undefined;
  if (!payload || !payload.details || Array.isArray(payload.details)) {
    return null;
  }

  const details = payload.details as Record<string, unknown>;
  const reasons = Array.isArray(details.reasons)
    ? details.reasons.filter((reason): reason is string => typeof reason === "string")
    : [];
  const recommendedStartTime = typeof details.recommendedStartTime === "string"
    ? details.recommendedStartTime
    : null;
  const recommendationSummary = typeof details.recommendationSummary === "string"
    ? details.recommendationSummary
    : null;

  if (!reasons.length && !recommendedStartTime && !recommendationSummary) {
    return null;
  }

  return {
    reservationId: typeof details.reservationId === "number" ? details.reservationId : undefined,
    reasons,
    recommendedStartTime,
    recommendationSummary,
  };
}

function AvailabilityConflictCard({ details }: { details: AvailabilityConflictDetails }) {
  const { t } = useI18n();
  return (
    <div className="rounded-lg border border-rose-300/30 bg-rose-500/10 p-5 text-sm text-rose-50">
      <p className="font-semibold text-white">{t("No table is available")}</p>
      <p className="mt-2 text-rose-100/90">
        {t("The reservation cannot be assigned at the requested time.")}
      </p>

      {details.reasons?.length ? (
        <ul className="mt-3 grid gap-2">
          {details.reasons.map((reason) => (
            <li key={reason} className="rounded-lg border border-rose-200/15 bg-slate-950/35 px-3 py-2">
              {t(humanizeAvailabilityReason(reason))}
            </li>
          ))}
        </ul>
      ) : null}

      <div className="mt-4 rounded-lg border border-brand-300/30 bg-brand-400/10 p-3 text-brand-50">
        <p className="text-xs font-semibold uppercase text-brand-200">
          {t("Next option")}
        </p>
        {details.recommendedStartTime ? (
          <>
            <p className="mt-2 text-2xl font-semibold text-white">
              {normalizeTimeForInput(details.recommendedStartTime)}
            </p>
            <p className="mt-1 text-sm text-brand-100">
              {t("Change the time and confirm again.")}
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

function humanizeAvailabilityReason(reason: string) {
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
