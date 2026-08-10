import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus, RotateCcw } from "lucide-react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type {
  CreateCustomerPayload,
  CreateReservationPayload,
  ReservationChannel,
} from "@/features/frontdesk/types";
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
import { useI18n } from "@/features/i18n/I18nProvider";

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
  customerMode: "quick" as "quick" | "existing",
  customerSearch: "",
  customerId: "",
  customerFirstName: "",
  customerLastName: "",
  customerPhone: "",
  customerEmail: "",
  customerNotes: "",
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
  initialDate?: string;
  onCreated: (reservationId: number) => void;
}

export function ReservationForm({
  canManageReservations,
  initialDate,
  onCreated,
}: ReservationFormProps) {
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const [form, setForm] = useState({
    ...defaultForm,
    reservationDate: initialDate ?? defaultForm.reservationDate,
  });
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

  useEffect(() => {
    if (!initialDate) {
      return;
    }

    setForm((current) => ({
      ...current,
      reservationDate: initialDate,
    }));
  }, [initialDate]);

  function resetForm() {
    setForm({
      ...defaultForm,
      reservationDate: initialDate ?? defaultForm.reservationDate,
    });
    setValidationError(null);
    setSubmitError(null);
  }

  function validateForm() {
    if (form.customerMode === "existing" && !form.customerId) {
      return t("Select a customer for the reservation.");
    }

    if (
      form.customerMode === "quick" &&
      !form.customerFirstName.trim() &&
      !form.customerLastName.trim() &&
      !form.customerPhone.trim()
    ) {
      return t("Enter at least a first name, last name or phone number.");
    }

    if (Number(form.partySize) <= 0) {
      return t("The number of guests must be greater than zero.");
    }

    if (!form.reservationDate) {
      return t("Reservation date is required.");
    }

    if (!form.startTime) {
      return t("Start time is required.");
    }

    if (Number(form.estimatedDurationMin) <= 0) {
      return t("Estimated duration must be greater than zero.");
    }

    if (Number(form.cleaningBufferMin) < 0) {
      return t("Cleaning time cannot be negative.");
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

    try {
      let customerId = Number(form.customerId);

      if (form.customerMode === "quick") {
        const customerPayload: CreateCustomerPayload = {
          firstName: form.customerFirstName.trim() || null,
          lastName: form.customerLastName.trim() || null,
          phone: form.customerPhone.trim() || null,
          email: form.customerEmail.trim() || null,
          notes: form.customerNotes.trim() || null,
          tagsJson: null,
          mobilityNeeds: form.accessibilityRequired ? "Accessibility required for reservation" : null,
        };

        const customer = await frontdeskApi.createCustomer(activeRestaurantId, customerPayload);
        customerId = customer.id;
      }

      const payload: CreateReservationPayload = {
        customerId,
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
          {t("Your role can view reservations, but cannot create them.")}
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
        <section className="rounded-lg border border-white/10 bg-white/5 p-4">
          <div className="grid gap-2 sm:grid-cols-2">
            <button
              type="button"
              className={[
                "min-h-11 rounded-lg px-4 py-2 text-sm font-semibold transition",
                form.customerMode === "quick"
                  ? "bg-brand-500 text-slate-950"
                  : "border border-white/10 bg-slate-950/50 text-slate-200 hover:border-brand-300/40",
              ].join(" ")}
              onClick={() =>
                setForm((current) => ({
                  ...current,
                  customerMode: "quick",
                  customerId: "",
                }))
              }
            >
              {t("New customer")}
            </button>
            <button
              type="button"
              className={[
                "min-h-11 rounded-lg px-4 py-2 text-sm font-semibold transition",
                form.customerMode === "existing"
                  ? "bg-brand-500 text-slate-950"
                  : "border border-white/10 bg-slate-950/50 text-slate-200 hover:border-brand-300/40",
              ].join(" ")}
              onClick={() =>
                setForm((current) => ({
                  ...current,
                  customerMode: "existing",
                }))
              }
            >
              {t("Existing customer")}
            </button>
          </div>

          {form.customerMode === "quick" ? (
            <div className="mt-4 grid gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t("First name")}
                  value={form.customerFirstName}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, customerFirstName: event.target.value }))
                  }
                  placeholder="John"
                />
                <TextField
                  label={t("Last name")}
                  value={form.customerLastName}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, customerLastName: event.target.value }))
                  }
                  placeholder="Smith"
                />
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t("Phone")}
                  type="tel"
                  value={form.customerPhone}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, customerPhone: event.target.value }))
                  }
                  placeholder="+353..."
                />
                <TextField
                  label={t("Email")}
                  type="email"
                  value={form.customerEmail}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, customerEmail: event.target.value }))
                  }
                  placeholder="customer@example.com"
                />
              </div>
              <TextAreaField
                label={t("Customer notes")}
                value={form.customerNotes}
                onChange={(event) =>
                  setForm((current) => ({ ...current, customerNotes: event.target.value }))
                }
                placeholder={t("Customer preferences")}
              />
            </div>
          ) : (
            <div className="mt-4 grid gap-4">
              <TextField
                label={t("Find customer")}
                value={form.customerSearch}
                onChange={(event) =>
                  setForm((current) => ({ ...current, customerSearch: event.target.value }))
                }
                placeholder={t("Name or phone")}
              />

              <SelectField
                label={t("Customer")}
                value={form.customerId}
                onChange={(event) =>
                  setForm((current) => ({ ...current, customerId: event.target.value }))
                }
              >
                <option value="" disabled>
                  {t("Select a customer")}
                </option>
                {customerOptions.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {formatCustomerName(customer)}
                    {customer.phone ? ` · ${customer.phone}` : ""}
                  </option>
                ))}
              </SelectField>
            </div>
          )}
        </section>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={t("Date")}
            type="date"
            value={form.reservationDate}
            onChange={(event) =>
              setForm((current) => ({ ...current, reservationDate: event.target.value }))
            }
          />
          <TextField
            label={t("Time")}
            type="time"
            value={form.startTime}
            onChange={(event) =>
              setForm((current) => ({ ...current, startTime: event.target.value }))
            }
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={t("Guests")}
            type="number"
            min={1}
            value={form.partySize}
            onChange={(event) =>
              setForm((current) => ({ ...current, partySize: event.target.value }))
            }
          />
          <TextField
            label={t("Duration")}
            type="number"
            min={1}
            value={form.estimatedDurationMin}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                estimatedDurationMin: event.target.value,
              }))
            }
            hint={t("Minutes")}
          />
          <TextField
            label={t("Cleaning")}
            type="number"
            min={0}
            value={form.cleaningBufferMin}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                cleaningBufferMin: event.target.value,
              }))
            }
            hint={t("Minutes")}
          />
          <SelectField
            label={t("Channel")}
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
          label={t("Accessibility required")}
          checked={form.accessibilityRequired}
          onChange={(checked) =>
            setForm((current) => ({ ...current, accessibilityRequired: checked }))
          }
        />

        <TextAreaField
          label={t("Special requests")}
          value={form.specialRequests}
          onChange={(event) =>
            setForm((current) => ({ ...current, specialRequests: event.target.value }))
          }
          placeholder={t("Allergies, celebration, high chair")}
        />
      </div>

      <div className="flex flex-wrap justify-end gap-3">
        <button
          className="h-12 rounded-lg border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-white/20"
          type="button"
          onClick={resetForm}
        >
          <span className="inline-flex items-center gap-2">
            <RotateCcw className="h-4 w-4" />
            {t("Clear")}
          </span>
        </button>
        <button
          className="h-12 rounded-lg bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
          type="button"
          disabled={!canManageReservations || isSubmitting}
          onClick={() => {
            void handleSubmit();
          }}
        >
          <span className="inline-flex items-center gap-2">
            <Plus className="h-4 w-4" />
            {isSubmitting
              ? t("Creating...")
              : form.customerMode === "quick"
                ? t("Create customer and reservation")
                : t("Create reservation")}
          </span>
        </button>
      </div>
    </div>
  );
}
