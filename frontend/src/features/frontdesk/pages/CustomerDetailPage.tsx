import { useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  Mail,
  Phone,
  Save,
  Trash2,
} from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import { OperationsShell } from "@/features/frontdesk/components/OperationsShell";
import {
  formatCustomerName,
  normalizeTagsForInput,
  tagsInputToJson,
} from "@/features/frontdesk/utils/frontdeskUtils";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import {
  TextAreaField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/features/i18n/I18nProvider";

const emptyForm = {
  firstName: "",
  lastName: "",
  phone: "",
  email: "",
  notes: "",
  tags: "",
  mobilityNeeds: "",
};

export function CustomerDetailPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { customerId } = useParams();
  const { session } = useAuth();
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const parsedCustomerId = Number(customerId);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const activeRoles =
    session.restaurants.find((restaurant) => restaurant.id === activeRestaurantId)?.roles ?? [];
  const canManageCustomers = activeRoles.some((role) =>
    ["PLATFORM_ADMIN", "RESTAURANT_OWNER", "MANAGER"].includes(role),
  );

  const customerQuery = useQuery({
    queryKey: ["customer", activeRestaurantId, parsedCustomerId],
    queryFn: () => frontdeskApi.getCustomer(activeRestaurantId!, parsedCustomerId),
    enabled: activeRestaurantId !== null && Number.isFinite(parsedCustomerId),
  });

  useEffect(() => {
    if (!customerQuery.data) {
      return;
    }
    setForm({
      firstName: customerQuery.data.firstName ?? "",
      lastName: customerQuery.data.lastName ?? "",
      phone: customerQuery.data.phone ?? "",
      email: customerQuery.data.email ?? "",
      notes: customerQuery.data.notes ?? "",
      tags: normalizeTagsForInput(customerQuery.data.tagsJson),
      mobilityNeeds: customerQuery.data.mobilityNeeds ?? "",
    });
  }, [customerQuery.data]);

  const updateMutation = useMutation({
    mutationFn: () =>
      frontdeskApi.updateCustomer(activeRestaurantId!, parsedCustomerId, {
        firstName: form.firstName.trim() || null,
        lastName: form.lastName.trim() || null,
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        notes: form.notes.trim() || null,
        tagsJson: tagsInputToJson(form.tags),
        mobilityNeeds: form.mobilityNeeds.trim() || null,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["customer", activeRestaurantId, parsedCustomerId],
        }),
        queryClient.invalidateQueries({ queryKey: ["customers", activeRestaurantId] }),
      ]);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () =>
      frontdeskApi.deleteCustomer(activeRestaurantId!, parsedCustomerId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["customers", activeRestaurantId],
      });
      navigate("/customers", { replace: true });
    },
  });

  function validateForm() {
    if (!form.phone.trim() && !form.firstName.trim() && !form.lastName.trim()) {
      return t("Enter a phone number or at least one name.");
    }
    return null;
  }

  return (
    <OperationsShell title={t("Customer profile")}>
      <div className="flex items-center justify-between gap-3">
        <Link
          className="inline-flex h-10 items-center gap-2 rounded-lg px-3 text-sm font-medium text-slate-300 transition hover:bg-white/5 hover:text-white"
          to="/customers"
        >
          <ArrowLeft className="h-4 w-4" />
          {t("Back to customers")}
        </Link>
        {canManageCustomers && customerQuery.data ? (
          <button
            type="button"
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-rose-400/20 px-3 text-sm font-medium text-rose-300 transition hover:bg-rose-500/10"
            onClick={() => setDeleteDialogOpen(true)}
          >
            <Trash2 className="h-4 w-4" />
            {t("Delete customer")}
          </button>
        ) : null}
      </div>

      {customerQuery.isLoading ? (
        <StatusMessage tone="info">{t("Loading profile...")}</StatusMessage>
      ) : null}
      {customerQuery.error ? (
        <StatusMessage tone="error">{getErrorMessage(customerQuery.error)}</StatusMessage>
      ) : null}

      {customerQuery.data ? (
        <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
          <ConfigCard title={t("Overview")}>
            <h2 className="text-xl font-semibold text-white">
              {formatCustomerName(customerQuery.data)}
            </h2>
            <div className="mt-5 grid gap-3 text-sm">
              <div className="flex items-center gap-3 text-slate-300">
                <Phone className="h-4 w-4 text-slate-500" />
                <span>{customerQuery.data.phone || t("No phone")}</span>
              </div>
              <div className="flex items-center gap-3 text-slate-300">
                <Mail className="h-4 w-4 text-slate-500" />
                <span className="truncate">
                  {customerQuery.data.email || t("No email")}
                </span>
              </div>
            </div>
            <div className="mt-6 border-t border-white/8 pt-4">
              <p className="text-xs text-slate-500">{t("Updated")}</p>
              <p className="mt-1 text-sm text-slate-300">
                {new Date(customerQuery.data.updatedAt).toLocaleString("en-GB")}
              </p>
            </div>
          </ConfigCard>

          <ConfigCard title={t("Customer details")}>
            {!canManageCustomers ? (
              <StatusMessage tone="info">
                {t("Your role can view customers but cannot edit them.")}
              </StatusMessage>
            ) : null}
            {validationError ? (
              <StatusMessage tone="error">{validationError}</StatusMessage>
            ) : null}
            {updateMutation.error ? (
              <StatusMessage tone="error">
                {getErrorMessage(updateMutation.error)}
              </StatusMessage>
            ) : null}

            <form
              className="grid gap-4"
              onSubmit={(event) => {
                event.preventDefault();
                const nextValidationError = validateForm();
                if (nextValidationError) {
                  setValidationError(nextValidationError);
                  return;
                }
                setValidationError(null);
                updateMutation.mutate();
              }}
            >
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t("First name")}
                  value={form.firstName}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      firstName: event.target.value,
                    }))
                  }
                />
                <TextField
                  label={t("Last name")}
                  value={form.lastName}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      lastName: event.target.value,
                    }))
                  }
                />
                <TextField
                  label={t("Phone")}
                  value={form.phone}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, phone: event.target.value }))
                  }
                />
                <TextField
                  label={t("Email")}
                  type="email"
                  value={form.email}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, email: event.target.value }))
                  }
                />
              </div>
              <TextField
                label={t("Tags")}
                value={form.tags}
                onChange={(event) =>
                  setForm((current) => ({ ...current, tags: event.target.value }))
                }
              />
              <TextField
                label={t("Accessibility needs")}
                value={form.mobilityNeeds}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    mobilityNeeds: event.target.value,
                  }))
                }
              />
              <TextAreaField
                label={t("Notes")}
                value={form.notes}
                onChange={(event) =>
                  setForm((current) => ({ ...current, notes: event.target.value }))
                }
              />

              <div className="flex justify-end">
                <button
                  className="inline-flex h-10 items-center gap-2 rounded-lg bg-emerald-400 px-4 text-sm font-semibold text-[#0b110e] transition hover:bg-emerald-300 disabled:opacity-60"
                  type="submit"
                  disabled={!canManageCustomers || updateMutation.isPending}
                >
                  <Save className="h-4 w-4" />
                  {updateMutation.isPending
                    ? t("Saving...")
                    : t("Save customer")}
                </button>
              </div>
            </form>
          </ConfigCard>
        </div>
      ) : null}

      {deleteDialogOpen && customerQuery.data ? (
        <div
          className="fixed inset-0 z-[70] grid place-items-center bg-black/70 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) {
              setDeleteDialogOpen(false);
            }
          }}
        >
          <div
            className="w-full max-w-md rounded-lg border border-white/10 bg-[#151a18] p-5 shadow-2xl"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-customer-title"
          >
            <div className="flex gap-3">
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-rose-500/10 text-rose-300">
                <AlertTriangle className="h-5 w-5" />
              </span>
              <div>
                <h2 id="delete-customer-title" className="text-lg font-semibold text-white">
                  {t("Delete customer")}
                </h2>
                <p className="mt-2 text-sm leading-6 text-slate-400">
                  {t("This action cannot be undone.")}
                </p>
              </div>
            </div>

            {deleteMutation.error ? (
              <div className="mt-4">
                <StatusMessage tone="error">
                  {getDeleteErrorMessage(deleteMutation.error, t)}
                </StatusMessage>
              </div>
            ) : null}

            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                className="h-10 rounded-lg px-4 text-sm font-medium text-slate-300 hover:bg-white/5"
                onClick={() => setDeleteDialogOpen(false)}
                disabled={deleteMutation.isPending}
              >
                {t("Cancel")}
              </button>
              <button
                type="button"
                className="inline-flex h-10 items-center gap-2 rounded-lg bg-rose-500 px-4 text-sm font-semibold text-white hover:bg-rose-400 disabled:opacity-60"
                onClick={() => deleteMutation.mutate()}
                disabled={deleteMutation.isPending}
              >
                <Trash2 className="h-4 w-4" />
                {deleteMutation.isPending
                  ? t("Deleting...")
                  : t("Delete permanently")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </OperationsShell>
  );
}

function getDeleteErrorMessage(
  error: unknown,
  t: (key: string) => string,
) {
  const message = getErrorMessage(error);
  if (message.includes("reservations are linked")) {
    return t("Customers with linked reservations cannot be deleted.");
  }
  return message;
}
