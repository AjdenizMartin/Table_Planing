import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { SelectField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useI18n } from "@/features/i18n/I18nProvider";

const timezones = [
  "Europe/Dublin",
  "Europe/Madrid",
  "Europe/London",
  "Europe/Paris",
  "America/New_York",
];

export function RestaurantSettingsPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const [validationError, setValidationError] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: "",
    slug: "",
    timezone: "Europe/Dublin",
    phone: "",
    status: "ACTIVE",
  });

  const restaurantQuery = useQuery({
    queryKey: ["restaurant", activeRestaurantId],
    queryFn: () => configApi.getRestaurant(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  useEffect(() => {
    if (restaurantQuery.data) {
      setForm({
        name: restaurantQuery.data.name,
        slug: restaurantQuery.data.slug,
        timezone: restaurantQuery.data.timezone,
        phone: restaurantQuery.data.phone ?? "",
        status: restaurantQuery.data.status,
      });
    }
  }, [restaurantQuery.data]);

  const updateMutation = useMutation({
    mutationFn: () =>
      configApi.updateRestaurant(activeRestaurantId!, {
        name: form.name,
        slug: form.slug,
        timezone: form.timezone,
        phone: form.phone || null,
        status: form.status as "ACTIVE" | "INACTIVE",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["restaurant", activeRestaurantId] });
    },
  });

  function validateForm() {
    if (!form.name.trim()) {
      return t("Restaurant name is required.");
    }

    if (!form.slug.trim()) {
      return t("The web identifier is required.");
    }

    if (!form.timezone.trim()) {
      return t("Time zone is required.");
    }

    return null;
  }

  return (
    <ConfigShell title={t("Restaurant")}>
      <ConfigCard title={t("Restaurant details")}>
        {restaurantQuery.isLoading ? (
          <StatusMessage tone="info">{t("Loading restaurant details...")}</StatusMessage>
        ) : null}
        {restaurantQuery.error ? (
          <StatusMessage tone="error">
            {getErrorMessage(restaurantQuery.error)}
          </StatusMessage>
        ) : null}
        {updateMutation.error ? (
          <StatusMessage tone="error">
            {getErrorMessage(updateMutation.error)}
          </StatusMessage>
        ) : null}
        {validationError ? (
          <StatusMessage tone="error">{validationError}</StatusMessage>
        ) : null}
        {restaurantQuery.data ? (
          <form
            className="grid gap-4 lg:grid-cols-2"
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
            <TextField
              label={t("First name")}
              value={form.name}
              onChange={(event) =>
                setForm((current) => ({ ...current, name: event.target.value }))
              }
              required
            />
            <TextField
              label={t("Web identifier")}
              value={form.slug}
              onChange={(event) =>
                setForm((current) => ({ ...current, slug: event.target.value }))
              }
              required
            />
            <SelectField
              label={t("Time zone")}
              value={form.timezone}
              onChange={(event) =>
                setForm((current) => ({ ...current, timezone: event.target.value }))
              }
            >
              {timezones.map((timezone) => (
                <option key={timezone} value={timezone}>
                  {timezone}
                </option>
              ))}
            </SelectField>
            <TextField
              label={t("Phone")}
              value={form.phone}
              onChange={(event) =>
                setForm((current) => ({ ...current, phone: event.target.value }))
              }
              placeholder="+34 600 000 000"
            />
            <SelectField
              label={t("Status")}
              value={form.status}
              onChange={(event) =>
                setForm((current) => ({ ...current, status: event.target.value }))
              }
            >
              <option value="ACTIVE">{t("Active")}</option>
              <option value="INACTIVE">{t("Inactive")}</option>
            </SelectField>

            <div className="lg:col-span-2 flex justify-end">
              <button
                className="h-12 rounded-lg bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? t("Saving...") : t("Save changes")}
              </button>
            </div>
          </form>
        ) : null}
      </ConfigCard>
    </ConfigShell>
  );
}
