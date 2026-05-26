import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import {
  CheckboxField,
  SelectField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import type { RestaurantTableResponse } from "@/features/restaurant-config/types";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";

const emptyForm = {
  diningRoomId: "",
  code: "",
  label: "",
  minCapacity: "2",
  maxCapacity: "4",
  shape: "RECTANGLE",
  x: "100",
  y: "100",
  width: "120",
  height: "80",
  active: true,
};

export function TablesPage() {
  const queryClient = useQueryClient();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selected, setSelected] = useState<RestaurantTableResponse | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);

  const diningRoomsQuery = useQuery({
    queryKey: ["diningRooms", activeRestaurantId],
    queryFn: () => configApi.getDiningRooms(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  const tablesQuery = useQuery({
    queryKey: ["tables", activeRestaurantId],
    queryFn: () => configApi.getTables(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  useEffect(() => {
    if (!selected) {
      setForm((current) => ({
        ...emptyForm,
        diningRoomId:
          diningRoomsQuery.data && diningRoomsQuery.data.length > 0
            ? String(diningRoomsQuery.data[0].id)
            : "",
      }));
      return;
    }

    setForm({
      diningRoomId: String(selected.diningRoomId),
      code: selected.code,
      label: selected.label ?? "",
      minCapacity: String(selected.minCapacity),
      maxCapacity: String(selected.maxCapacity),
      shape: selected.shape,
      x: String(selected.x),
      y: String(selected.y),
      width: String(selected.width),
      height: String(selected.height),
      active: selected.active,
    });
  }, [selected, diningRoomsQuery.data]);

  const roomsById = useMemo(
    () =>
      new Map((diningRoomsQuery.data ?? []).map((room) => [room.id, room.name])),
    [diningRoomsQuery.data],
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["tables", activeRestaurantId] }),
      queryClient.invalidateQueries({ queryKey: ["diningRooms", activeRestaurantId] }),
    ]);
  };

  const createMutation = useMutation({
    mutationFn: () =>
      configApi.createTable(activeRestaurantId!, {
        diningRoomId: Number(form.diningRoomId),
        code: form.code,
        label: form.label || null,
        minCapacity: Number(form.minCapacity),
        maxCapacity: Number(form.maxCapacity),
        shape: form.shape,
        x: Number(form.x),
        y: Number(form.y),
        width: Number(form.width),
        height: Number(form.height),
        active: form.active,
      }),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      configApi.updateTable(activeRestaurantId!, selected!.id, {
        diningRoomId: Number(form.diningRoomId),
        code: form.code,
        label: form.label || null,
        minCapacity: Number(form.minCapacity),
        maxCapacity: Number(form.maxCapacity),
        shape: form.shape,
        active: form.active,
      }),
    onSuccess: async () => {
      if (!selected) {
        return;
      }

      await configApi.updateTableLayout(activeRestaurantId!, selected.id, {
        x: Number(form.x),
        y: Number(form.y),
        width: Number(form.width),
        height: Number(form.height),
      });
      setSelected(null);
      await refresh();
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (tableId: number) => configApi.deactivateTable(activeRestaurantId!, tableId),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const feedbackError =
    createMutation.error ?? updateMutation.error ?? deactivateMutation.error;

  function validateForm() {
    if (!form.diningRoomId) {
      return "Selecciona un salon para la mesa.";
    }

    if (!form.code.trim()) {
      return "El codigo de la mesa es obligatorio.";
    }

    if (Number(form.minCapacity) <= 0 || Number(form.maxCapacity) <= 0) {
      return "Las capacidades deben ser mayores que cero.";
    }

    if (Number(form.minCapacity) > Number(form.maxCapacity)) {
      return "La capacidad minima no puede ser mayor que la maxima.";
    }

    if (
      Number(form.x) < 0 ||
      Number(form.y) < 0 ||
      Number(form.width) < 20 ||
      Number(form.height) < 20
    ) {
      return "Revisa posicion y tamaño de la mesa antes de guardar.";
    }

    return null;
  }

  return (
    <ConfigShell
      title="Mesas del restaurante"
      description="Configura las mesas del restaurante con capacidad, forma, salon y posicion visual. Las validaciones de capacidad y pertenencia al salon viven en backend."
    >
      <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <ConfigCard title="Mesas configuradas">
          {tablesQuery.isLoading ? (
            <StatusMessage tone="info">Cargando mesas...</StatusMessage>
          ) : null}
          {tablesQuery.error ? (
            <StatusMessage tone="error">{getErrorMessage(tablesQuery.error)}</StatusMessage>
          ) : null}

          <div className="grid gap-3">
            {tablesQuery.data?.map((table) => (
              <article
                key={table.id}
                className={[
                  "rounded-3xl border p-4 transition",
                  selected?.id === table.id
                    ? "border-brand-400/60 bg-brand-500/10"
                    : "border-white/10 bg-white/5",
                ].join(" ")}
              >
                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-white">
                      {table.code}
                      {table.label ? ` · ${table.label}` : ""}
                    </h3>
                    <p className="mt-2 text-sm text-slate-400">
                      Salon {roomsById.get(table.diningRoomId) ?? table.diningRoomId} ·{" "}
                      {table.minCapacity}-{table.maxCapacity} pax · {table.shape}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Posicion {table.x},{table.y} · {table.width} × {table.height} ·{" "}
                      {table.active ? "Activa" : "Inactiva"}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <button
                      className="h-11 rounded-2xl border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                      type="button"
                      onClick={() => setSelected(table)}
                    >
                      Editar
                    </button>
                    <button
                      className="h-11 rounded-2xl border border-white/10 bg-rose-500/10 px-4 text-sm font-medium text-rose-100 transition hover:border-rose-400/40"
                      type="button"
                      onClick={() => deactivateMutation.mutate(table.id)}
                    >
                      Desactivar
                    </button>
                  </div>
                </div>
              </article>
            ))}

            {tablesQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                Todavia no hay mesas creadas para este restaurante.
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard
          title={selected ? "Editar mesa" : "Crear mesa"}
          subtitle="Para facilitar el uso en tablet, los campos mas frecuentes quedan visibles sin abrir paneles secundarios."
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
            <SelectField
              label="Salon"
              value={form.diningRoomId}
              onChange={(event) =>
                setForm((current) => ({ ...current, diningRoomId: event.target.value }))
              }
            >
              <option value="" disabled>
                Selecciona un salon
              </option>
              {(diningRoomsQuery.data ?? []).map((room) => (
                <option key={room.id} value={room.id}>
                  {room.name}
                </option>
              ))}
            </SelectField>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Codigo"
                value={form.code}
                onChange={(event) =>
                  setForm((current) => ({ ...current, code: event.target.value }))
                }
                required
              />
              <TextField
                label="Etiqueta"
                value={form.label}
                onChange={(event) =>
                  setForm((current) => ({ ...current, label: event.target.value }))
                }
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <TextField
                label="Capacidad min"
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
                label="Capacidad max"
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
              <SelectField
                label="Forma"
                value={form.shape}
                onChange={(event) =>
                  setForm((current) => ({ ...current, shape: event.target.value }))
                }
              >
                <option value="RECTANGLE">RECTANGLE</option>
                <option value="ROUND">ROUND</option>
                <option value="SQUARE">SQUARE</option>
              </SelectField>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="X"
                type="number"
                min={0}
                value={form.x}
                onChange={(event) =>
                  setForm((current) => ({ ...current, x: event.target.value }))
                }
                required
              />
              <TextField
                label="Y"
                type="number"
                min={0}
                value={form.y}
                onChange={(event) =>
                  setForm((current) => ({ ...current, y: event.target.value }))
                }
                required
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Ancho"
                type="number"
                min={20}
                value={form.width}
                onChange={(event) =>
                  setForm((current) => ({ ...current, width: event.target.value }))
                }
                required
              />
              <TextField
                label="Alto"
                type="number"
                min={20}
                value={form.height}
                onChange={(event) =>
                  setForm((current) => ({ ...current, height: event.target.value }))
                }
                required
              />
            </div>
            <CheckboxField
              label="Mesa activa"
              checked={form.active}
              onChange={(checked) =>
                setForm((current) => ({ ...current, active: checked }))
              }
            />

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
                    : "Guardar mesa"
                  : createMutation.isPending
                    ? "Creando..."
                    : "Crear mesa"}
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </ConfigShell>
  );
}
