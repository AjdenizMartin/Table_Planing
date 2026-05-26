import { Link } from "react-router-dom";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
      return "Introduce telefono o al menos un nombre para crear el cliente.";
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
    <OperationsShell
      title="Clientes del restaurante"
      description="Busca clientes por nombre o telefono y crea nuevas fichas para enlazarlas despues a reservas manuales."
    >
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <ConfigCard title="Base de clientes">
          <div className="mb-5">
            <TextField
              label="Buscar cliente"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Nombre o telefono"
            />
          </div>

          {customersQuery.isLoading ? (
            <StatusMessage tone="info">Cargando clientes...</StatusMessage>
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
                className="rounded-3xl border border-white/10 bg-white/5 p-4"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-white">
                      {formatCustomerName(customer)}
                    </h3>
                    <p className="mt-2 text-sm text-slate-400">
                      {customer.phone || "Sin telefono"}
                      {customer.email ? ` · ${customer.email}` : ""}
                    </p>
                    {customer.mobilityNeeds ? (
                      <p className="mt-1 text-sm text-slate-500">
                        Movilidad: {customer.mobilityNeeds}
                      </p>
                    ) : null}
                  </div>

                  <Link
                    className="inline-flex h-11 items-center justify-center rounded-2xl border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                    to={`/customers/${customer.id}`}
                  >
                    Ver ficha
                  </Link>
                </div>
              </article>
            ))}

            {customersQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                No hay clientes para esta busqueda en el restaurante activo.
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard
          title="Nuevo cliente"
          subtitle="Crea una ficha ligera para poder reservar rapido desde tablet."
        >
          {!canManageCustomers ? (
            <StatusMessage tone="info">
              Tu rol actual puede consultar clientes, pero no crear nuevos.
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
              placeholder="VIP, alergias, habitual"
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
                disabled={!canManageCustomers || createMutation.isPending}
              >
                {createMutation.isPending ? "Creando..." : "Crear cliente"}
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </OperationsShell>
  );
}
