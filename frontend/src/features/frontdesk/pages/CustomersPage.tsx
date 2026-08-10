import { Link } from "react-router-dom";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, UserPlus } from "lucide-react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import { OperationsShell } from "@/features/frontdesk/components/OperationsShell";
import type { CreateCustomerPayload } from "@/features/frontdesk/types";
import {
  formatCustomerName,
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

export function CustomersPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const [search, setSearch] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);

  const activeRoles =
    session.restaurants.find((restaurant) => restaurant.id === activeRestaurantId)?.roles ?? [];
  const canManageCustomers = activeRoles.some((role) =>
    ["PLATFORM_ADMIN", "RESTAURANT_OWNER", "MANAGER"].includes(role),
  );

  const customersQuery = useQuery({
    queryKey: ["customers", activeRestaurantId, search],
    queryFn: () => frontdeskApi.getCustomers(activeRestaurantId!, search || undefined),
    enabled: activeRestaurantId !== null,
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateCustomerPayload) =>
      frontdeskApi.createCustomer(activeRestaurantId!, payload),
    onSuccess: async () => {
      setForm(emptyForm);
      await queryClient.invalidateQueries({ queryKey: ["customers", activeRestaurantId] });
    },
  });

  function validateForm() {
    if (!form.phone.trim() && !form.firstName.trim() && !form.lastName.trim()) {
      return t("Enter a phone number or at least one name.");
    }

    return null;
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextValidationError = validateForm();
    if (nextValidationError) {
      setValidationError(nextValidationError);
      return;
    }

    setValidationError(null);
    createMutation.mutate({
      firstName: form.firstName.trim() || null,
      lastName: form.lastName.trim() || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      notes: form.notes.trim() || null,
      tagsJson: tagsInputToJson(form.tags),
      mobilityNeeds: form.mobilityNeeds.trim() || null,
    });
  }

  return (
    <OperationsShell title={t("Customers")}>
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <ConfigCard title={t("Customer list")}>
          <div className="mb-5">
            <TextField
              label={t("Find customer")}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t("Name or phone")}
            />
          </div>

          {customersQuery.isLoading ? (
            <StatusMessage tone="info">{t("Loading customers...")}</StatusMessage>
          ) : null}
          {customersQuery.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(customersQuery.error)}
            </StatusMessage>
          ) : null}

          <div className="grid gap-3">
            {customersQuery.data?.map((customer) => (
              <article
                key={customer.id}
                className="rounded-lg border border-white/10 bg-white/5 p-4"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-white">
                      {formatCustomerName(customer)}
                    </h3>
                    <p className="mt-2 text-sm text-slate-400">
                      {customer.phone || t("No phone")}
                      {customer.email ? ` · ${customer.email}` : ""}
                    </p>
                    {customer.mobilityNeeds ? (
                      <p className="mt-1 text-sm text-slate-500">
                        {t("Accessibility")}: {customer.mobilityNeeds}
                      </p>
                    ) : null}
                  </div>

                  <Link
                    className="inline-flex h-11 items-center justify-center rounded-lg border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                    to={`/customers/${customer.id}`}
                  >
                    <Eye className="h-4 w-4" />
                    {t("View profile")}
                  </Link>
                </div>
              </article>
            ))}

            {customersQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                {t("No customers match this search.")}
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard title={t("New customer")}>
          {!canManageCustomers ? (
            <StatusMessage tone="info">
              {t("Your role can view customers, but cannot create them.")}
            </StatusMessage>
          ) : null}
          {validationError ? <StatusMessage tone="error">{validationError}</StatusMessage> : null}
          {createMutation.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(createMutation.error)}
            </StatusMessage>
          ) : null}

          <form className="grid gap-4" onSubmit={handleSubmit}>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t("First name")}
                value={form.firstName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, firstName: event.target.value }))
                }
              />
              <TextField
                label={t("Last name")}
                value={form.lastName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, lastName: event.target.value }))
                }
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
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
              placeholder={t("VIP, allergies, regular")}
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
                className="h-12 rounded-lg bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={!canManageCustomers || createMutation.isPending}
              >
                <span className="inline-flex items-center gap-2">
                  <UserPlus className="h-4 w-4" />
                  {createMutation.isPending ? t("Creating...") : t("Create customer")}
                </span>
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </OperationsShell>
  );
}
