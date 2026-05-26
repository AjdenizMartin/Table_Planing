import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type { CreateReservationPayload, ReservationChannel } from "@/features/frontdesk/types";
import { formatCustomerName, todayDateValue } from "@/features/frontdesk/utils/frontdeskUtils";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import {
  CheckboxField,
  SelectField,
  TextAreaField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";

const channels: ReservationChannel[] = [
  "MANUAL",
  "PHONE",
  "WEB",
  "GOOGLE",
  "INSTAGRAM",
  "FACEBOOK",
  "WHATSAPP",
];

const defaultForm = {
  customerSearch: "",
  customerId: "",
  channel: "MANUAL" as ReservationChannel,
  partySize: "2",
  reservationDate: todayDateValue(),
  startTime: "20:00",
  estimatedDurationMin: "90",
  cleaningBufferMin: "15",
  specialRequests: "",
  accessibilityRequired: false,
};

interface ReservationFormProps {
  canManageReservations: boolean;
  onCreated: (reservationId: number) => void;
}

export function ReservationForm({
  canManageReservations,
  onCreated,
}: ReservationFormProps) {
  const { activeRestaurantId } = useActiveRestaurant();
  const [form, setForm] = useState(defaultForm);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const customersQuery = useQuery({
    queryKey: ["reservationCustomers", activeRestaurantId, form.customerSearch],
    queryFn: () =>
      frontdeskApi.getCustomers(activeRestaurantId!, form.customerSearch || undefined),
    enabled: activeRestaurantId !== null,
  });

  const customerOptions = useMemo(
    () => customersQuery.data ?? [],
    [customersQuery.data],
  );

  function resetForm() {
    setForm(defaultForm);
    setValidationError(null);
    setSubmitError(null);
  }

  function validateForm() {
    if (!form.customerId) {
      return "Selecciona un cliente para la reserva.";
    }

    if (Number(form.partySize) <= 0) {
      return "El numero de comensales debe ser mayor que cero.";
    }

    if (!form.reservationDate) {
      return "La fecha de la reserva es obligatoria.";
    }

    if (!form.startTime) {
      return "La hora de inicio es obligatoria.";
    }

    if (Number(form.estimatedDurationMin) <= 0) {
      return "La duracion estimada debe ser mayor que cero.";
    }

    if (Number(form.cleaningBufferMin) < 0) {
      return "El margen de limpieza no puede ser negativo.";
    }

    return null;
  }

  async function handleSubmit() {
    if (!activeRestaurantId || !canManageReservations) {
      return;
    }

    const nextValidationError = validateForm();
    if (nextValidationError) {
      setValidationError(nextValidationError);
      return;
    }

    setValidationError(null);
    setSubmitError(null);
    setIsSubmitting(true);

    const payload: CreateReservationPayload = {
      customerId: Number(form.customerId),
      channel: form.channel,
      partySize: Number(form.partySize),
      reservationDate: form.reservationDate,
      startTime: form.startTime,
      endTime: null,
      estimatedDurationMin: Number(form.estimatedDurationMin),
      cleaningBufferMin: Number(form.cleaningBufferMin),
      specialRequests: form.specialRequests.trim() || null,
      accessibilityRequired: form.accessibilityRequired,
    };

    try {
      const reservation = await frontdeskApi.createReservation(activeRestaurantId, payload);
      resetForm();
      onCreated(reservation.id);
    } catch (error) {
      setSubmitError(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="grid gap-4">
      {!canManageReservations ? (
        <StatusMessage tone="info">
          Tu rol actual puede consultar reservas, pero no crear nuevas.
        </StatusMessage>
      ) : null}
      {customersQuery.error ? (
        <StatusMessage tone="error">
          {getErrorMessage(customersQuery.error)}
        </StatusMessage>
      ) : null}
      {validationError ? <StatusMessage tone="error">{validationError}</StatusMessage> : null}
      {submitError ? <StatusMessage tone="error">{submitError}</StatusMessage> : null}

      <div className="grid gap-4">
        <TextField
          label="Buscar cliente"
          value={form.customerSearch}
          onChange={(event) =>
            setForm((current) => ({ ...current, customerSearch: event.target.value }))
          }
          placeholder="Nombre o telefono"
        />

        <SelectField
          label="Cliente"
          value={form.customerId}
          onChange={(event) =>
            setForm((current) => ({ ...current, customerId: event.target.value }))
          }
        >
          <option value="" disabled>
            Selecciona un cliente
          </option>
          {customerOptions.map((customer) => (
            <option key={customer.id} value={customer.id}>
              {formatCustomerName(customer)}
              {customer.phone ? ` · ${customer.phone}` : ""}
            </option>
          ))}
        </SelectField>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Fecha"
            type="date"
            value={form.reservationDate}
            onChange={(event) =>
              setForm((current) => ({ ...current, reservationDate: event.target.value }))
            }
          />
          <TextField
            label="Hora"
            type="time"
            value={form.startTime}
            onChange={(event) =>
              setForm((current) => ({ ...current, startTime: event.target.value }))
            }
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <TextField
            label="Comensales"
            type="number"
            min={1}
            value={form.partySize}
            onChange={(event) =>
              setForm((current) => ({ ...current, partySize: event.target.value }))
            }
          />
          <TextField
            label="Duracion"
            type="number"
            min={1}
            value={form.estimatedDurationMin}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                estimatedDurationMin: event.target.value,
              }))
            }
            hint="Minutos"
          />
          <TextField
            label="Limpieza"
            type="number"
            min={0}
            value={form.cleaningBufferMin}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                cleaningBufferMin: event.target.value,
              }))
            }
            hint="Minutos"
          />
          <SelectField
            label="Canal"
            value={form.channel}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                channel: event.target.value as ReservationChannel,
              }))
            }
          >
            {channels.map((channel) => (
              <option key={channel} value={channel}>
                {channel}
              </option>
            ))}
          </SelectField>
        </div>

        <CheckboxField
          label="Requiere accesibilidad"
          checked={form.accessibilityRequired}
          onChange={(checked) =>
            setForm((current) => ({ ...current, accessibilityRequired: checked }))
          }
        />

        <TextAreaField
          label="Peticiones especiales"
          value={form.specialRequests}
          onChange={(event) =>
            setForm((current) => ({ ...current, specialRequests: event.target.value }))
          }
          placeholder="Alergias, celebracion, silla infantil..."
        />
      </div>

      <div className="flex flex-wrap justify-end gap-3">
        <button
          className="h-12 rounded-2xl border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-white/20"
          type="button"
          onClick={resetForm}
        >
          Limpiar
        </button>
        <button
          className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
          type="button"
          disabled={!canManageReservations || isSubmitting}
          onClick={() => {
            void handleSubmit();
          }}
        >
          {isSubmitting ? "Creando..." : "Crear reserva"}
        </button>
      </div>
    </div>
  );
}
