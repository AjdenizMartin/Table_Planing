import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import { OperationsShell } from "@/features/frontdesk/components/OperationsShell";
import { formatCustomerName, normalizeTagsForInput, tagsInputToJson } from "@/features/frontdesk/utils/frontdeskUtils";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import {
  TextAreaField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useAuth } from "@/features/auth/context/AuthContext";

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
  const queryClient = useQueryClient();
  const { customerId } = useParams();
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const parsedCustomerId = Number(customerId);
  const [validationError, setValidationError] = useState<string | null>(null);
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
        queryClient.invalidateQueries({ queryKey: ["customer", activeRestaurantId, parsedCustomerId] }),
        queryClient.invalidateQueries({ queryKey: ["customers", activeRestaurantId] }),
      ]);
    },
  });

  function validateForm() {
    if (!form.phone.trim() && !form.firstName.trim() && !form.lastName.trim()) {
      return "Introduce telefono o al menos un nombre para este cliente.";
    }

    return null;
  }

  return (
    <OperationsShell
      title="Ficha de cliente"
      description="Consulta y actualiza la informacion operativa del cliente para futuras reservas."
    >
      <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <ConfigCard title="Resumen">
          <Link
            className="inline-flex h-11 items-center justify-center rounded-2xl border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
            to="/customers"
          >
            Volver a clientes
          </Link>

          {customerQuery.isLoading ? (
            <div className="mt-5">
              <StatusMessage tone="info">Cargando ficha del cliente...</StatusMessage>
            </div>
          ) : null}
          {customerQuery.error ? (
            <div className="mt-5">
              <StatusMessage tone="error">
                {getErrorMessage(customerQuery.error)}
              </StatusMessage>
            </div>
          ) : null}

          {customerQuery.data ? (
            <div className="mt-5 grid gap-4">
              <div className="rounded-3xl border border-white/10 bg-white/5 p-5">
                <h3 className="text-2xl font-semibold text-white">
                  {formatCustomerName(customerQuery.data)}
                </h3>
                <p className="mt-2 text-sm text-slate-400">
                  {customerQuery.data.phone || "Sin telefono"}
                  {customerQuery.data.email ? ` · ${customerQuery.data.email}` : ""}
                </p>
              </div>

              <div className="rounded-3xl border border-white/10 bg-slate-950/55 p-5">
                <p className="text-xs uppercase tracking-[0.2em] text-slate-500">
                  Actualizado
                </p>
                <p className="mt-2 text-sm text-slate-200">
                  {new Date(customerQuery.data.updatedAt).toLocaleString()}
                </p>
              </div>
            </div>
          ) : null}
        </ConfigCard>

        <ConfigCard title="Editar cliente">
          {!canManageCustomers ? (
            <StatusMessage tone="info">
              Tu rol actual puede consultar clientes, pero no editar esta ficha.
            </StatusMessage>
          ) : null}
          {validationError ? <StatusMessage tone="error">{validationError}</StatusMessage> : null}
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
                label="Nombre"
                value={form.firstName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, firstName: event.target.value }))
                }
              />
              <TextField
                label="Apellidos"
                value={form.lastName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, lastName: event.target.value }))
                }
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Telefono"
                value={form.phone}
                onChange={(event) =>
                  setForm((current) => ({ ...current, phone: event.target.value }))
                }
              />
              <TextField
                label="Email"
                type="email"
                value={form.email}
                onChange={(event) =>
                  setForm((current) => ({ ...current, email: event.target.value }))
                }
              />
            </div>
            <TextField
              label="Etiquetas"
              value={form.tags}
              onChange={(event) =>
                setForm((current) => ({ ...current, tags: event.target.value }))
              }
              hint="Separadas por comas"
            />
            <TextField
              label="Necesidades de movilidad"
              value={form.mobilityNeeds}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  mobilityNeeds: event.target.value,
                }))
              }
            />
            <TextAreaField
              label="Notas"
              value={form.notes}
              onChange={(event) =>
                setForm((current) => ({ ...current, notes: event.target.value }))
              }
            />

            <div className="flex justify-end">
              <button
                className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={!canManageCustomers || updateMutation.isPending}
              >
                {updateMutation.isPending ? "Guardando..." : "Guardar cliente"}
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </OperationsShell>
  );
}
