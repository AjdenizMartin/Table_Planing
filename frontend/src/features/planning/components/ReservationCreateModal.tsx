import { useEffect, useState } from "react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import * as planningApi from "@/features/planning/api/planningApi";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { notify } from "@/features/notifications/components/NotificationToast";
import type { AssignReservationResponse } from "@/features/planning/types";

interface Props {
  open: boolean;
  restaurantId: number;
  selectedDate: string;
  onClose: () => void;
  onCreated: (reservationId: number) => void;
}

function formatMinutesDiff(requested: string, alternative: string): string {
  const [rh, rm] = requested.split(":").map(Number);
  const [ah, am] = alternative.split(":").map(Number);
  const diff = (ah * 60 + am) - (rh * 60 + rm);
  if (diff === 0) return "same time";
  const abs = Math.abs(diff);
  const label = abs >= 60
    ? `${Math.floor(abs / 60)}h ${abs % 60 > 0 ? `${abs % 60} min` : ""}`
    : `${abs} min`;
  return diff < 0 ? `${label} earlier` : `${label} later`;
}

export function ReservationCreateModal({
  open,
  restaurantId,
  selectedDate,
  onClose,
  onCreated,
}: Props) {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [phone, setPhone] = useState("");
  const [partySize, setPartySize] = useState("2");
  const [startTime, setStartTime] = useState("20:00");
  const [customerNotes, setCustomerNotes] = useState("");
  const [specialRequests, setSpecialRequests] = useState("");
  const [accessibilityRequired, setAccessibilityRequired] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [assignResult, setAssignResult] = useState<AssignReservationResponse | null>(null);
  const [createdReservationId, setCreatedReservationId] = useState<number | null>(null);
  const [retryingWithTime, setRetryingWithTime] = useState(false);

  useEffect(() => {
    if (!open) {
      setAssignResult(null);
      setCreatedReservationId(null);
      setRetryingWithTime(false);
      setError(null);
      setIsSubmitting(false);
      return;
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onClose]);

  function validate(): string | null {
    if (!firstName.trim() && !lastName.trim() && !phone.trim()) {
      return "Enter at least a name or phone.";
    }
    const size = Number(partySize);
    if (!Number.isInteger(size) || size < 1) {
      return "Party size must be at least 1.";
    }
    if (!startTime) {
      return "Start time is required.";
    }
    return null;
  }

  async function handleCreate() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setIsSubmitting(true);
    setAssignResult(null);

    try {
      const customer = await frontdeskApi.createCustomer(restaurantId, {
        firstName: firstName.trim() || null,
        lastName: lastName.trim() || null,
        phone: phone.trim() || null,
        email: null,
        notes: customerNotes.trim() || null,
        tagsJson: null,
        mobilityNeeds: null,
      });

      const reservation = await frontdeskApi.createReservation(restaurantId, {
        customerId: customer.id,
        channel: "MANUAL",
        partySize: Number(partySize),
        reservationDate: selectedDate,
        startTime,
        endTime: null,
        estimatedDurationMin: 90,
        cleaningBufferMin: 15,
        specialRequests: specialRequests.trim() || null,
        accessibilityRequired,
      });

      setCreatedReservationId(reservation.id);

      const assignResponse = await planningApi.assignReservationAutomatically(restaurantId, reservation.id);
      setAssignResult(assignResponse);

      if (assignResponse.assigned) {
        notify("Reservation created and table assigned.", "");
        onCreated(reservation.id);
        onClose();
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRetryWithAlternative(time: string) {
    if (!createdReservationId) return;
    setError(null);
    setRetryingWithTime(true);

    try {
      await frontdeskApi.updateReservation(restaurantId, createdReservationId, {
        startTime: time,
      });

      const assignResponse = await planningApi.assignReservationAutomatically(restaurantId, createdReservationId);
      setAssignResult(assignResponse);

      if (assignResponse.assigned) {
        notify("Reservation created and table assigned.", "");
        onCreated(createdReservationId);
        onClose();
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setRetryingWithTime(false);
    }
  }

  if (!open) return null;

  const requestedTime = startTime;
  const alternativeTime = assignResult?.recommendedStartTime ?? null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm">
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
      <div className="absolute inset-0" onClick={isSubmitting || retryingWithTime ? undefined : onClose} />

      <div
        className="relative mx-4 flex max-h-[90vh] w-full max-w-lg flex-col rounded-[2rem] border border-white/10 bg-slate-950 shadow-2xl shadow-black/50"
        role="dialog"
        aria-label="New reservation"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-white/10 px-6 pb-4 pt-5">
          <div>
            <h2 className="text-xl font-semibold text-white">New reservation</h2>
            <p className="mt-1 text-xs text-slate-400">{selectedDate}</p>
          </div>
          <button
            type="button"
            className="flex h-10 w-10 items-center justify-center rounded-2xl border border-white/10 bg-white/5 text-base text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
            onClick={onClose}
            disabled={isSubmitting || retryingWithTime}
            aria-label="Close modal"
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <div className="overflow-y-auto px-6 py-5">
          {assignResult && !assignResult.assigned && alternativeTime ? (
            /* --- Alternatives view --- */
            <div className="space-y-5">
              <div className="rounded-3xl border border-amber-300/25 bg-amber-400/10 p-4 text-sm text-amber-100">
                <p className="font-semibold text-white">No table available at {requestedTime}</p>
                <p className="mt-2">
                  {assignResult.recommendationSummary ?? "The system could not find a suitable table for the requested time."}
                </p>
              </div>

              <div>
                <p className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                  Alternative times
                </p>
                <button
                  type="button"
                  className="w-full rounded-2xl border border-brand-300/40 bg-brand-500/15 px-4 py-4 text-left transition hover:bg-brand-500/25"
                  disabled={retryingWithTime}
                  onClick={() => handleRetryWithAlternative(alternativeTime)}
                >
                  <p className="text-lg font-semibold text-white">{alternativeTime}</p>
                  <p className="mt-1 text-sm text-slate-300">
                    {formatMinutesDiff(requestedTime, alternativeTime)}
                  </p>
                </button>
              </div>

              <div className="rounded-2xl border border-white/10 bg-slate-900/50 p-3 text-xs text-slate-400">
                {assignResult.reasons?.map((reason) => (
                  <p key={reason} className="mt-1 first:mt-0">{reason}</p>
                ))}
              </div>

              <button
                type="button"
                className="w-full h-12 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-white/30"
                onClick={onClose}
              >
                Close
              </button>
            </div>
          ) : assignResult && !assignResult.assigned && !alternativeTime ? (
            /* --- No alternatives available --- */
            <div className="space-y-4">
              <div className="rounded-3xl border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
                <p className="font-semibold text-white">Could not find a table</p>
                <p className="mt-2">{assignResult.summary ?? "No table is available for the requested parameters."}</p>
              </div>
              <button
                type="button"
                className="w-full h-12 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-white/30"
                onClick={onClose}
              >
                Close
              </button>
            </div>
          ) : (
            /* --- Form view --- */
            <div className="space-y-5">
              {error ? (
                <div className="rounded-3xl border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
                  {error}
                </div>
              ) : null}

              {/* Customer */}
              <section>
                <p className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                  Customer
                </p>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">First name</span>
                      <input
                        className="h-12 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                      />
                    </label>
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">Last name</span>
                      <input
                        className="h-12 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                      />
                    </label>
                  </div>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">Phone</span>
                    <input
                      className="h-12 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      type="tel"
                    />
                  </label>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">Notes</span>
                    <textarea
                      className="min-h-20 rounded-2xl border border-white/10 bg-slate-900/90 px-4 py-3 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      value={customerNotes}
                      onChange={(e) => setCustomerNotes(e.target.value)}
                      maxLength={4000}
                    />
                  </label>
                </div>
              </section>

              {/* Reservation */}
              <section>
                <p className="mb-3 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                  Reservation
                </p>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">Party size</span>
                      <input
                        className="h-12 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        type="number"
                        min={1}
                        value={partySize}
                        onChange={(e) => setPartySize(e.target.value)}
                      />
                    </label>
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">Time</span>
                      <input
                        className="h-12 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        type="time"
                        value={startTime}
                        onChange={(e) => setStartTime(e.target.value)}
                      />
                    </label>
                  </div>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">Special requests</span>
                    <textarea
                      className="min-h-20 rounded-2xl border border-white/10 bg-slate-900/90 px-4 py-3 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      value={specialRequests}
                      onChange={(e) => setSpecialRequests(e.target.value)}
                      maxLength={4000}
                    />
                  </label>
                  <label className="flex min-h-12 items-center gap-3 rounded-2xl border border-white/10 bg-slate-900/70 px-4 py-3">
                    <input
                      className="h-5 w-5 accent-brand-400"
                      type="checkbox"
                      checked={accessibilityRequired}
                      onChange={(e) => setAccessibilityRequired(e.target.checked)}
                    />
                    <span className="text-sm font-medium text-slate-200">Accessibility required</span>
                  </label>
                </div>
              </section>
            </div>
          )}
        </div>

        {/* Footer (only in form view) */}
        {!assignResult ? (
          <div className="flex gap-3 border-t border-white/10 px-6 pb-5 pt-4">
            <button
              type="button"
              className="flex-1 h-12 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-white/30"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="flex-1 h-12 rounded-2xl bg-brand-500 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isSubmitting}
              onClick={() => { void handleCreate(); }}
            >
              {isSubmitting ? "Creating..." : "Create and find table"}
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
