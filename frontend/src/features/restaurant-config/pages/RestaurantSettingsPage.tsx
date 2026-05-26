import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { SelectField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";

const timezones = [
  "Europe/Dublin",
  "Europe/Madrid",
  "Europe/London",
  "Europe/Paris",
  "America/New_York",
];

export function RestaurantSettingsPage() {
  const queryClient = useQueryClient();
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
      return "El nombre del restaurante es obligatorio.";
    }

    if (!form.slug.trim()) {
      return "El slug es obligatorio.";
    }

    if (!form.timezone.trim()) {
      return "La zona horaria es obligatoria.";
    }

    return null;
  }

  return (
    <ConfigShell
      title="Datos del restaurante"
      description="Ajusta el perfil principal del restaurante activo sin tocar codigo. Esta pantalla cubre nombre, slug, timezone, telefono y estado general."
    >
      <ConfigCard
        title="Ajustes principales"
        subtitle="El backend mantiene las validaciones criticas; aqui solo pedimos los datos necesarios y mostramos cualquier error de forma clara."
      >
        {restaurantQuery.isLoading ? (
          <StatusMessage tone="info">Cargando datos del restaurante...</StatusMessage>
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
              label="Nombre"
              value={form.name}
              onChange={(event) =>
                setForm((current) => ({ ...current, name: event.target.value }))
              }
              required
            />
            <TextField
              label="Slug"
              value={form.slug}
              onChange={(event) =>
                setForm((current) => ({ ...current, slug: event.target.value }))
              }
              required
            />
            <SelectField
              label="Timezone"
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
              label="Telefono"
              value={form.phone}
              onChange={(event) =>
                setForm((current) => ({ ...current, phone: event.target.value }))
              }
              placeholder="+34 600 000 000"
            />
            <SelectField
              label="Estado"
              value={form.status}
              onChange={(event) =>
                setForm((current) => ({ ...current, status: event.target.value }))
              }
            >
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </SelectField>

            <div className="lg:col-span-2 flex justify-end">
              <button
                className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? "Guardando..." : "Guardar cambios"}
              </button>
            </div>
          </form>
        ) : null}
      </ConfigCard>
    </ConfigShell>
  );
}
