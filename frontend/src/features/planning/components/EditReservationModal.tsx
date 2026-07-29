import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type { CustomerResponse, ReservationResponse } from "@/features/frontdesk/types";
import type { PlanningReservationSummaryResponse } from "@/features/planning/types";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { notify } from "@/features/notifications/components/NotificationToast";
import { ChevronDown, ChevronUp, X } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

interface Props {
  open: boolean;
  reservationId: number;
  customerId: number | null;
  fullReservation: ReservationResponse | undefined;
  customer: CustomerResponse | undefined;
  reservationSummary: PlanningReservationSummaryResponse | null;
  restaurantId: number;
  selectedDate: string;
  onClose: () => void;
}

function hasCustomerChanges(
  c: CustomerResponse | undefined,
  firstName: string,
  lastName: string,
  phone: string,
  customerNotes: string,
) {
  if (!c) return false;
  return (
    (firstName.trim() || null) !== (c.firstName ?? null) ||
    (lastName.trim() || null) !== (c.lastName ?? null) ||
    (phone.trim() || null) !== (c.phone ?? null) ||
    (customerNotes.trim() || null) !== (c.notes ?? null)
  );
}

function hasReservationChanges(
  r: ReservationResponse | undefined,
  partySize: string,
  specialRequests: string,
  accessibilityRequired: boolean,
) {
  if (!r) return false;
  return (
    Number(partySize) !== r.partySize ||
    (specialRequests.trim() || null) !== (r.specialRequests ?? null) ||
    accessibilityRequired !== r.accessibilityRequired
  );
}

function hasTimeChanges(
  r: ReservationResponse | undefined,
  reservationDate: string,
  startTime: string,
  estimatedDurationMin: string,
  cleaningBufferMin: string,
) {
  if (!r) return false;
  return (
    reservationDate !== r.reservationDate ||
    startTime !== r.startTime.slice(0, 5) ||
    Number(estimatedDurationMin) !== r.estimatedDurationMin ||
    Number(cleaningBufferMin) !== r.cleaningBufferMin
  );
}

export function EditReservationModal({
  open,
  reservationId,
  customerId,
  fullReservation,
  customer,
  reservationSummary,
  restaurantId,
  selectedDate,
  onClose,
}: Props) {
  const queryClient = useQueryClient();
  const { t } = useI18n();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [phone, setPhone] = useState("");
  const [customerNotes, setCustomerNotes] = useState("");
  const [partySize, setPartySize] = useState("");
  const [specialRequests, setSpecialRequests] = useState("");
  const [accessibilityRequired, setAccessibilityRequired] = useState(false);
  const [reservationDate, setReservationDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [estimatedDurationMin, setEstimatedDurationMin] = useState("");
  const [cleaningBufferMin, setCleaningBufferMin] = useState("");
  const [timeExpanded, setTimeExpanded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (!open) {
      setInitialized(false);
      setTimeExpanded(false);
      return;
    }
    if (fullReservation && !initialized) {
      setFirstName(customer?.firstName ?? "");
      setLastName(customer?.lastName ?? "");
      setPhone(customer?.phone ?? "");
      setCustomerNotes(customer?.notes ?? "");
      setPartySize(String(fullReservation.partySize));
      setSpecialRequests(fullReservation.specialRequests ?? "");
      setAccessibilityRequired(fullReservation.accessibilityRequired);
      setReservationDate(fullReservation.reservationDate);
      setStartTime(fullReservation.startTime.slice(0, 5));
      setEstimatedDurationMin(String(fullReservation.estimatedDurationMin));
      setCleaningBufferMin(String(fullReservation.cleaningBufferMin));
      setError(null);
      setInitialized(true);
    }
  }, [open, fullReservation, customer, initialized]);

  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onClose]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const size = Number(partySize);
      if (!Number.isInteger(size) || size < 1) {
        throw new Error(t("El numero de comensales debe ser mayor que cero."));
      }

      const customerChanged = hasCustomerChanges(customer, firstName, lastName, phone, customerNotes);
      const reservationChanged = hasReservationChanges(fullReservation, partySize, specialRequests, accessibilityRequired);
      const timeChanged = timeExpanded && hasTimeChanges(fullReservation, reservationDate, startTime, estimatedDurationMin, cleaningBufferMin);

      const promises: Promise<unknown>[] = [];

      if (customerChanged && customerId) {
        promises.push(
          frontdeskApi.updateCustomer(restaurantId, customerId, {
            firstName: firstName.trim() || null,
            lastName: lastName.trim() || null,
            phone: phone.trim() || null,
            notes: customerNotes.trim() || null,
            email: customer?.email ?? null,
            tagsJson: customer?.tagsJson ?? null,
            mobilityNeeds: customer?.mobilityNeeds ?? null,
          }),
        );
      }

      if (reservationChanged || timeChanged) {
        const payload: Record<string, unknown> = {};
        if (reservationChanged) {
          payload.partySize = size;
          payload.specialRequests = specialRequests.trim() || null;
          payload.accessibilityRequired = accessibilityRequired;
        }
        if (timeChanged) {
          payload.reservationDate = reservationDate;
          payload.startTime = startTime;
          payload.estimatedDurationMin = Number(estimatedDurationMin);
          payload.cleaningBufferMin = Number(cleaningBufferMin);
        }
        promises.push(
          frontdeskApi.updateReservation(restaurantId, reservationId, payload),
        );
      }

      if (promises.length === 0) {
        throw new Error(t("No hay cambios que guardar."));
      }

      await Promise.all(promises);
    },
    onSuccess: () => {
      setError(null);
      notify(t("Reserva actualizada."), "");
      queryClient.invalidateQueries({ queryKey: ["planning", restaurantId, selectedDate] });
      queryClient.invalidateQueries({ queryKey: ["reservation-detail", restaurantId, reservationId] });
      if (customerId) {
        queryClient.invalidateQueries({ queryKey: ["customer", restaurantId, customerId] });
      }
      onClose();
    },
    onError: (err) => {
      setError(getErrorMessage(err));
    },
  });

  if (!open) return null;

  const customerChanged = hasCustomerChanges(customer, firstName, lastName, phone, customerNotes);
  const reservationChanged = hasReservationChanges(fullReservation, partySize, specialRequests, accessibilityRequired);
  const timeChanged = timeExpanded && hasTimeChanges(fullReservation, reservationDate, startTime, estimatedDurationMin, cleaningBufferMin);
  const hasAnyChanges = customerChanged || reservationChanged || timeChanged;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm">
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
      <div className="absolute inset-0" onClick={saveMutation.isPending ? undefined : onClose} />

      <div
        className="relative mx-4 flex max-h-[90vh] w-full max-w-lg flex-col rounded-lg border border-white/10 bg-slate-950 shadow-2xl shadow-black/50"
        role="dialog"
        aria-label={t("Editar reserva")}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-white/10 px-6 pb-4 pt-5">
          <div>
            <h2 className="text-xl font-semibold text-white">{t("Editar reserva")}</h2>
            <p className="mt-1 text-xs text-slate-400">
              {fullReservation ? `${t("Reserva")} #${fullReservation.id}` : ""}
            </p>
          </div>
          <button
            type="button"
            className="flex h-10 w-10 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-base text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
            onClick={onClose}
            disabled={saveMutation.isPending}
            aria-label={t("Cerrar")}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Body */}
        <div className="overflow-y-auto px-6 py-5">
          <div className="space-y-6">
            {error ? (
              <div className="rounded-lg border border-rose-300/30 bg-rose-500/10 p-4 text-sm text-rose-100">
                {error}
              </div>
            ) : null}

            {/* --- Customer section --- */}
            <section>
              <p className="mb-3 text-xs font-semibold uppercase text-slate-500">
                {t("Cliente")}
              </p>
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">{t("Nombre")}</span>
                    <input
                      className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                    />
                  </label>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">{t("Apellidos")}</span>
                    <input
                      className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      value={lastName}
                      onChange={(e) => setLastName(e.target.value)}
                    />
                  </label>
                </div>
                <label className="grid gap-2">
                  <span className="text-sm font-medium text-slate-200">{t("Telefono")}</span>
                  <input
                    className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    type="tel"
                  />
                </label>
                <label className="grid gap-2">
                  <span className="text-sm font-medium text-slate-200">{t("Notas del cliente")}</span>
                  <textarea
                    className="min-h-20 rounded-lg border border-white/10 bg-slate-900/90 px-4 py-3 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                    value={customerNotes}
                    onChange={(e) => setCustomerNotes(e.target.value)}
                    maxLength={4000}
                  />
                </label>
              </div>
            </section>

            {/* --- Reservation section --- */}
            <section>
              <p className="mb-3 text-xs font-semibold uppercase text-slate-500">
                {t("Reserva")}
              </p>
              <div className="space-y-3">
                <label className="grid gap-2">
                  <span className="text-sm font-medium text-slate-200">{t("Comensales")}</span>
                  <input
                    className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                    type="number"
                    min={1}
                    value={partySize}
                    onChange={(e) => setPartySize(e.target.value)}
                  />
                </label>
                <label className="grid gap-2">
                  <span className="text-sm font-medium text-slate-200">{t("Peticiones especiales")}</span>
                  <textarea
                    className="min-h-20 rounded-lg border border-white/10 bg-slate-900/90 px-4 py-3 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                    value={specialRequests}
                    onChange={(e) => setSpecialRequests(e.target.value)}
                    maxLength={4000}
                  />
                </label>
                <label className="flex min-h-12 items-center gap-3 rounded-lg border border-white/10 bg-slate-900/70 px-4 py-3">
                  <input
                    className="h-5 w-5 accent-brand-400"
                    type="checkbox"
                    checked={accessibilityRequired}
                    onChange={(e) => setAccessibilityRequired(e.target.checked)}
                  />
                  <span className="text-sm font-medium text-slate-200">{t("Requiere accesibilidad")}</span>
                </label>
              </div>
            </section>

            {/* --- Status section (read-only) --- */}
            {reservationSummary ? (
              <section>
                <p className="mb-3 text-xs font-semibold uppercase text-slate-500">
                  {t("Estado")}
                </p>
                <div className="flex items-center gap-3 rounded-lg border border-white/10 bg-slate-900/70 px-4 py-3">
                  <StatusPill status={reservationSummary.status} />
                </div>
              </section>
            ) : null}

            {/* --- Table section (read-only) --- */}
            {reservationSummary ? (
              <section>
                <p className="mb-3 text-xs font-semibold uppercase text-slate-500">
                  {t("Mesa")}
                </p>
                <div className="rounded-lg border border-white/10 bg-slate-900/70 px-4 py-3 text-sm text-white">
                  {reservationSummary.tableCode ?? reservationSummary.tableCombinationName ?? (
                    <span className="text-slate-400">{t("Sin asignar")}</span>
                  )}
                </div>
              </section>
            ) : null}

            {/* --- Time section (collapsed) --- */}
            <section>
              <button
                type="button"
                className="flex w-full items-center justify-between rounded-lg border border-amber-300/25 bg-amber-400/10 px-4 py-3 text-sm font-semibold text-amber-100 transition hover:bg-amber-400/20"
                onClick={() => setTimeExpanded((prev) => !prev)}
              >
                <span>{t("Editar fecha y hora")}</span>
                {timeExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
              </button>

              {timeExpanded ? (
                <div className="mt-3 space-y-3 rounded-lg border border-amber-300/20 bg-amber-400/[0.04] p-4">
                  <div className="rounded-lg border border-amber-300/20 bg-amber-400/10 px-3 py-2 text-xs text-amber-100">
                    {t("Cambia la hora solo si lo solicita el cliente.")}
                  </div>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">{t("Fecha")}</span>
                    <input
                      className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      type="date"
                      value={reservationDate}
                      onChange={(e) => setReservationDate(e.target.value)}
                    />
                  </label>
                  <label className="grid gap-2">
                    <span className="text-sm font-medium text-slate-200">{t("Hora")}</span>
                    <input
                      className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                      type="time"
                      value={startTime}
                      onChange={(e) => setStartTime(e.target.value)}
                    />
                  </label>
                  <div className="grid grid-cols-2 gap-3">
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">{t("Duracion")} (min)</span>
                      <input
                        className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        type="number"
                        min={15}
                        step={15}
                        value={estimatedDurationMin}
                        onChange={(e) => setEstimatedDurationMin(e.target.value)}
                      />
                    </label>
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-slate-200">{t("Limpieza")} (min)</span>
                      <input
                        className="h-12 rounded-lg border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                        type="number"
                        min={0}
                        step={5}
                        value={cleaningBufferMin}
                        onChange={(e) => setCleaningBufferMin(e.target.value)}
                      />
                    </label>
                  </div>
                </div>
              ) : null}
            </section>
          </div>
        </div>

        {/* Footer */}
        <div className="flex gap-3 border-t border-white/10 px-6 pb-5 pt-4">
          <button
            type="button"
            className="flex-1 h-12 rounded-lg border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-white/30"
            onClick={onClose}
            disabled={saveMutation.isPending}
          >
            {t("Cancelar")}
          </button>
          <button
            type="button"
            className="flex-1 h-12 rounded-lg bg-brand-500 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={saveMutation.isPending || !hasAnyChanges}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("Guardando...") : t("Guardar cambios")}
          </button>
        </div>
      </div>
    </div>
  );
}
