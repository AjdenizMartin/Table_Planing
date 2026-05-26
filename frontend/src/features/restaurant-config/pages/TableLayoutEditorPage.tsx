import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { SelectField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";

export function TableLayoutEditorPage() {
  const queryClient = useQueryClient();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [selectedTableId, setSelectedTableId] = useState<number | null>(null);
  const [draft, setDraft] = useState({
    x: "",
    y: "",
    width: "",
    height: "",
  });
  const [validationError, setValidationError] = useState<string | null>(null);

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

  const availableRooms = diningRoomsQuery.data ?? [];
  const roomId = selectedRoomId ?? availableRooms[0]?.id ?? null;
  const tablesInRoom = (tablesQuery.data ?? []).filter((table) => table.diningRoomId === roomId);
  const selectedTable =
    tablesInRoom.find((table) => table.id === selectedTableId) ?? tablesInRoom[0] ?? null;
  const selectedRoom = availableRooms.find((room) => room.id === roomId) ?? null;

  const boardScale = useMemo(() => {
    if (!selectedRoom) {
      return 1;
    }
    return Math.min(1, 680 / selectedRoom.layoutWidth);
  }, [selectedRoom]);

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["tables", activeRestaurantId] });
  };

  const updateLayoutMutation = useMutation({
    mutationFn: (payload: { x: number; y: number; width: number; height: number }) =>
      configApi.updateTableLayout(activeRestaurantId!, selectedTable!.id, payload),
    onSuccess: async () => {
      await refresh();
    },
  });

  const selectedTableSignature = selectedTable
    ? `${selectedTable.id}:${selectedTable.x}:${selectedTable.y}:${selectedTable.width}:${selectedTable.height}`
    : "empty";

  useEffect(() => {
    if (!selectedTable) {
      setDraft({ x: "", y: "", width: "", height: "" });
      return;
    }

    setDraft({
      x: String(selectedTable.x),
      y: String(selectedTable.y),
      width: String(selectedTable.width),
      height: String(selectedTable.height),
    });
  }, [selectedTableSignature]);

  function validateDraft() {
    const x = Number(draft.x);
    const y = Number(draft.y);
    const width = Number(draft.width);
    const height = Number(draft.height);

    if ([x, y, width, height].some((value) => Number.isNaN(value))) {
      return "Introduce valores numericos validos para el layout.";
    }

    if (x < 0 || y < 0) {
      return "Las coordenadas no pueden ser negativas.";
    }

    if (width < 20 || height < 20) {
      return "El tamaño de la mesa debe ser razonable para el plano.";
    }

    return null;
  }

  function applyDraft() {
    if (!selectedTable) {
      return;
    }

    const nextValidationError = validateDraft();
    if (nextValidationError) {
      setValidationError(nextValidationError);
      return;
    }

    setValidationError(null);
    updateLayoutMutation.mutate({
      x: Number(draft.x),
      y: Number(draft.y),
      width: Number(draft.width),
      height: Number(draft.height),
    });
  }

  function patchSelectedTable(delta: Partial<{ x: number; y: number; width: number; height: number }>) {
    if (!selectedTable) {
      return;
    }

    const nextDraft = {
      x: String(delta.x ?? selectedTable.x),
      y: String(delta.y ?? selectedTable.y),
      width: String(delta.width ?? selectedTable.width),
      height: String(delta.height ?? selectedTable.height),
    };

    setDraft(nextDraft);
    setValidationError(null);
    updateLayoutMutation.mutate({
      x: Number(nextDraft.x),
      y: Number(nextDraft.y),
      width: Number(nextDraft.width),
      height: Number(nextDraft.height),
    });
  }

  return (
    <ConfigShell
      title="Editor de plano visual"
      description="Ajusta la posicion y el tamaño de las mesas dentro del salon activo. El backend sigue validando coordenadas y dimensiones razonables."
    >
      <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <ConfigCard
          title="Plano del salon"
          subtitle="Selecciona un salon y una mesa para reposicionarla con controles grandes y precisos para tablet."
        >
          {diningRoomsQuery.error || tablesQuery.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(diningRoomsQuery.error ?? tablesQuery.error)}
            </StatusMessage>
          ) : null}

          <div className="mb-5 grid gap-4 sm:grid-cols-2">
            <SelectField
              label="Salon"
              value={roomId ?? ""}
              onChange={(event) => {
                setSelectedRoomId(Number(event.target.value));
                setSelectedTableId(null);
              }}
            >
              {availableRooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.name}
                </option>
              ))}
            </SelectField>

            <SelectField
              label="Mesa"
              value={selectedTable?.id ?? ""}
              onChange={(event) => setSelectedTableId(Number(event.target.value))}
            >
              {tablesInRoom.map((table) => (
                <option key={table.id} value={table.id}>
                  {table.code}
                  {table.label ? ` · ${table.label}` : ""}
                </option>
              ))}
            </SelectField>
          </div>

          {selectedRoom ? (
            <div className="overflow-auto rounded-[1.75rem] border border-white/10 bg-slate-950/80 p-4">
              <div
                className="relative overflow-hidden rounded-[1.5rem] border border-white/10 bg-[linear-gradient(135deg,rgba(255,255,255,0.03),transparent)]"
                style={{
                  width: selectedRoom.layoutWidth * boardScale,
                  height: selectedRoom.layoutHeight * boardScale,
                  minWidth: "280px",
                }}
              >
                {tablesInRoom.map((table) => (
                  <button
                    key={table.id}
                    type="button"
                    className={[
                      "absolute rounded-2xl border text-xs font-semibold transition",
                      selectedTable?.id === table.id
                        ? "border-brand-300 bg-brand-500 text-slate-950"
                        : "border-white/10 bg-slate-900/90 text-white hover:border-brand-400/40",
                    ].join(" ")}
                    style={{
                      left: table.x * boardScale,
                      top: table.y * boardScale,
                      width: Math.max(56, table.width * boardScale),
                      height: Math.max(48, table.height * boardScale),
                    }}
                    onClick={() => setSelectedTableId(table.id)}
                  >
                    {table.code}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <StatusMessage tone="info">
              Crea un salon y al menos una mesa para empezar a editar el layout.
            </StatusMessage>
          )}
        </ConfigCard>

        <ConfigCard title="Controles de posicion">
          {updateLayoutMutation.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(updateLayoutMutation.error)}
            </StatusMessage>
          ) : null}
          {validationError ? (
            <StatusMessage tone="error">{validationError}</StatusMessage>
          ) : null}

          {selectedTable ? (
            <div className="grid gap-5">
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <h3 className="text-lg font-semibold text-white">
                  {selectedTable.code}
                  {selectedTable.label ? ` · ${selectedTable.label}` : ""}
                </h3>
                <p className="mt-2 text-sm text-slate-400">
                  Posicion {selectedTable.x},{selectedTable.y} · Tamaño {selectedTable.width} × {selectedTable.height}
                </p>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <button
                  className="col-start-2 h-14 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => patchSelectedTable({ y: Math.max(0, selectedTable.y - 20) })}
                >
                  Arriba
                </button>
                <button
                  className="h-14 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => patchSelectedTable({ x: Math.max(0, selectedTable.x - 20) })}
                >
                  Izquierda
                </button>
                <button
                  className="h-14 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => patchSelectedTable({ x: selectedTable.x + 20 })}
                >
                  Derecha
                </button>
                <button
                  className="col-start-2 h-14 rounded-2xl border border-white/10 bg-white/5 text-sm font-semibold text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => patchSelectedTable({ y: selectedTable.y + 20 })}
                >
                  Abajo
                </button>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label="Posicion X"
                  type="number"
                  value={draft.x}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, x: event.target.value }))
                  }
                />
                <TextField
                  label="Posicion Y"
                  type="number"
                  value={draft.y}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, y: event.target.value }))
                  }
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label="Ancho"
                  type="number"
                  value={draft.width}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, width: event.target.value }))
                  }
                />
                <TextField
                  label="Alto"
                  type="number"
                  value={draft.height}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, height: event.target.value }))
                  }
                />
              </div>

              <button
                className="h-12 rounded-2xl bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="button"
                disabled={updateLayoutMutation.isPending}
                onClick={applyDraft}
              >
                {updateLayoutMutation.isPending ? "Aplicando..." : "Aplicar layout"}
              </button>
            </div>
          ) : (
            <StatusMessage tone="info">
              Selecciona una mesa para ajustar su posicion dentro del plano.
            </StatusMessage>
          )}
        </ConfigCard>
      </div>
    </ConfigShell>
  );
}
