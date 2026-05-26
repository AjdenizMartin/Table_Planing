import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { CheckboxField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import type { TableCombinationResponse } from "@/features/restaurant-config/types";

const emptyForm = {
  name: "",
  minCapacity: "2",
  maxCapacity: "4",
  active: true,
  tableIds: [] as number[],
};

export function TableCombinationsPage() {
  const queryClient = useQueryClient();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selected, setSelected] = useState<TableCombinationResponse | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);

  const tablesQuery = useQuery({
    queryKey: ["tables", activeRestaurantId],
    queryFn: () => configApi.getTables(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  const combinationsQuery = useQuery({
    queryKey: ["tableCombinations", activeRestaurantId],
    queryFn: () => configApi.getTableCombinations(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  useEffect(() => {
    if (!selected) {
      setForm(emptyForm);
      return;
    }

    setForm({
      name: selected.name,
      minCapacity: String(selected.minCapacity),
      maxCapacity: String(selected.maxCapacity),
      active: selected.active,
      tableIds: selected.items.map((item) => item.tableId),
    });
  }, [selected]);

  const availableTables = useMemo(
    () => (tablesQuery.data ?? []).filter((table) => table.active),
    [tablesQuery.data],
  );

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["tableCombinations", activeRestaurantId] });

  const createMutation = useMutation({
    mutationFn: () =>
      configApi.createTableCombination(activeRestaurantId!, {
        name: form.name,
        minCapacity: Number(form.minCapacity),
        maxCapacity: Number(form.maxCapacity),
        active: form.active,
        tableIds: form.tableIds,
      }),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      configApi.updateTableCombination(activeRestaurantId!, selected!.id, {
        name: form.name,
        minCapacity: Number(form.minCapacity),
        maxCapacity: Number(form.maxCapacity),
        active: form.active,
        tableIds: form.tableIds,
      }),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (combinationId: number) =>
      configApi.deactivateTableCombination(activeRestaurantId!, combinationId),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const feedbackError =
    createMutation.error ?? updateMutation.error ?? deactivateMutation.error;

  function validateForm() {
    if (!form.name.trim()) {
      return "El nombre de la combinacion es obligatorio.";
    }

    if (form.tableIds.length < 2) {
      return "Selecciona al menos dos mesas para crear una combinacion.";
    }

    if (Number(form.minCapacity) <= 0 || Number(form.maxCapacity) <= 0) {
      return "Las capacidades deben ser mayores que cero.";
    }

    if (Number(form.minCapacity) > Number(form.maxCapacity)) {
      return "La capacidad minima no puede superar la maxima.";
    }

    return null;
  }

  function toggleTable(tableId: number) {
    setForm((current) => ({
      ...current,
      tableIds: current.tableIds.includes(tableId)
        ? current.tableIds.filter((id) => id !== tableId)
        : [...current.tableIds, tableId],
    }));
  }

  return (
    <ConfigShell
      title="Combinaciones reutilizables"
      description="Define las combinaciones de mesas que el algoritmo puede usar mas tarde. Esta pantalla solo consume mesas activas y deja las validaciones estructurales al backend."
    >
      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <ConfigCard title="Combinaciones activas">
          {combinationsQuery.isLoading ? (
            <StatusMessage tone="info">Cargando combinaciones...</StatusMessage>
          ) : null}
          {combinationsQuery.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(combinationsQuery.error)}
            </StatusMessage>
          ) : null}

          <div className="grid gap-3">
            {combinationsQuery.data?.map((combination) => (
              <article
                key={combination.id}
                className={[
                  "rounded-3xl border p-4 transition",
                  selected?.id === combination.id
                    ? "border-brand-400/60 bg-brand-500/10"
                    : "border-white/10 bg-white/5",
                ].join(" ")}
              >
                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-white">{combination.name}</h3>
                    <p className="mt-2 text-sm text-slate-400">
                      Capacidad {combination.minCapacity}-{combination.maxCapacity} pax
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Mesas:{" "}
                      {combination.items
                        .map((item) => item.tableCode)
                        .join(" + ")}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button
                      className="h-11 rounded-2xl border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                      type="button"
                      onClick={() => setSelected(combination)}
                    >
                      Editar
                    </button>
                    <button
                      className="h-11 rounded-2xl border border-white/10 bg-rose-500/10 px-4 text-sm font-medium text-rose-100 transition hover:border-rose-400/40"
                      type="button"
                      onClick={() => deactivateMutation.mutate(combination.id)}
                    >
                      Desactivar
                    </button>
                  </div>
                </div>
              </article>
            ))}

            {combinationsQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                Todavia no hay combinaciones activas para este restaurante.
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard
          title={selected ? "Editar combinacion" : "Crear combinacion"}
          subtitle="Selecciona al menos dos mesas. El backend decidira si la combinacion es valida para el restaurante."
        >
          {feedbackError ? (
            <StatusMessage tone="error">{getErrorMessage(feedbackError)}</StatusMessage>
          ) : null}
          {validationError ? (
            <StatusMessage tone="error">{validationError}</StatusMessage>
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
              if (selected) {
                updateMutation.mutate();
              } else {
                createMutation.mutate();
              }
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
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Capacidad minima"
                type="number"
                min={1}
                value={form.minCapacity}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    minCapacity: event.target.value,
                  }))
                }
                required
              />
              <TextField
                label="Capacidad maxima"
                type="number"
                min={1}
                value={form.maxCapacity}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    maxCapacity: event.target.value,
                  }))
                }
                required
              />
            </div>
            <CheckboxField
              label="Combinacion activa"
              checked={form.active}
              onChange={(checked) =>
                setForm((current) => ({ ...current, active: checked }))
              }
            />

            <div className="grid gap-3">
              <div>
                <h3 className="text-sm font-medium text-slate-200">
                  Mesas incluidas
                </h3>
                <p className="mt-1 text-xs text-slate-500">
                  El orden de seleccion actual se envia al backend tal como aparece.
                </p>
              </div>
              <div className="grid gap-2">
                {availableTables.map((table) => (
                  <label
                    key={table.id}
                    className="flex min-h-12 items-center gap-3 rounded-2xl border border-white/10 bg-slate-900/70 px-4 py-3"
                  >
                    <input
                      className="h-5 w-5 accent-brand-400"
                      type="checkbox"
                      checked={form.tableIds.includes(table.id)}
                      onChange={() => toggleTable(table.id)}
                    />
                    <span className="text-sm text-slate-200">
                      {table.code}
                      {table.label ? ` · ${table.label}` : ""} · {table.minCapacity}-{table.maxCapacity} pax
                    </span>
                  </label>
                ))}
              </div>
            </div>

            <div className="flex flex-wrap justify-end gap-3">
              {selected ? (
                <button
                  className="h-12 rounded-2xl border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-white/20"
                  type="button"
                  onClick={() => {
                    setSelected(null);
                    setValidationError(null);
                  }}
                >
                  Cancelar
                </button>
              ) : null}
              <button
                className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {selected
                  ? updateMutation.isPending
                    ? "Guardando..."
                    : "Guardar combinacion"
                  : createMutation.isPending
                    ? "Creando..."
                    : "Crear combinacion"}
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </ConfigShell>
  );
}
