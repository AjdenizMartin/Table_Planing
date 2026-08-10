import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { SelectField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import {
  useDraggableTable,
  type TableLayoutDraft,
} from "@/features/restaurant-config/hooks/useDraggableTable";
import type {
  DiningRoomResponse,
  RestaurantTableResponse,
} from "@/features/restaurant-config/types";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useI18n } from "@/features/i18n/I18nProvider";

interface UpdateLayoutVariables {
  tableId: number;
  layout: TableLayoutDraft;
  previous: TableLayoutDraft;
}

function layoutFromTable(table: RestaurantTableResponse): TableLayoutDraft {
  return {
    x: table.x,
    y: table.y,
    width: table.width,
    height: table.height,
  };
}

function sameLayout(left: TableLayoutDraft, right: TableLayoutDraft) {
  return (
    left.x === right.x &&
    left.y === right.y &&
    left.width === right.width &&
    left.height === right.height
  );
}

function clampLayoutToRoom(layout: TableLayoutDraft, room: DiningRoomResponse) {
  const width = Math.min(layout.width, room.layoutWidth);
  const height = Math.min(layout.height, room.layoutHeight);
  return {
    x: Math.max(0, Math.min(layout.x, Math.max(0, room.layoutWidth - width))),
    y: Math.max(0, Math.min(layout.y, Math.max(0, room.layoutHeight - height))),
    width,
    height,
  };
}

export function TableLayoutEditorPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [selectedTableId, setSelectedTableId] = useState<number | null>(null);
  const [tableLayouts, setTableLayouts] = useState<Record<number, TableLayoutDraft>>({});
  const [snapToGrid, setSnapToGrid] = useState(true);
  const [saveError, setSaveError] = useState<string | null>(null);
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
  const tablesWithLocalLayout = (tablesQuery.data ?? []).map((table) => ({
    ...table,
    ...(tableLayouts[table.id] ?? {}),
  }));
  const tablesInRoom = tablesWithLocalLayout.filter((table) => table.diningRoomId === roomId);
  const selectedTable =
    tablesInRoom.find((table) => table.id === selectedTableId) ?? tablesInRoom[0] ?? null;
  const selectedRoom = availableRooms.find((room) => room.id === roomId) ?? null;

  const boardScale = useMemo(() => {
    if (!selectedRoom) {
      return 1;
    }
    return Math.min(1, 680 / selectedRoom.layoutWidth);
  }, [selectedRoom]);

  const updateLayoutMutation = useMutation({
    mutationFn: ({ tableId, layout }: UpdateLayoutVariables) =>
      configApi.updateTableLayout(activeRestaurantId!, tableId, layout),
    onSuccess: async (updatedTable) => {
      setSaveError(null);
      setTableLayouts((current) => ({
        ...current,
        [updatedTable.id]: layoutFromTable(updatedTable),
      }));
      queryClient.setQueryData<RestaurantTableResponse[]>(
        ["tables", activeRestaurantId],
        (current = []) =>
          current.map((table) => (table.id === updatedTable.id ? updatedTable : table)),
      );
      await queryClient.invalidateQueries({ queryKey: ["planning", activeRestaurantId] });
    },
    onError: (_error, variables) => {
      setSaveError("Could not save table position. Please try again.");
      setTableLayouts((current) => ({
        ...current,
        [variables.tableId]: variables.previous,
      }));
    },
  });

  const selectedTableSignature = selectedTable
    ? `${selectedTable.id}:${selectedTable.x}:${selectedTable.y}:${selectedTable.width}:${selectedTable.height}`
    : "empty";

  useEffect(() => {
    if (!tablesQuery.data) {
      return;
    }

    setTableLayouts((current) => {
      const next = { ...current };
      for (const table of tablesQuery.data) {
        if (!next[table.id]) {
          next[table.id] = layoutFromTable(table);
        }
      }
      return next;
    });
  }, [tablesQuery.data]);

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
      return "Enter valid numeric values for the floor plan.";
    }

    if (x < 0 || y < 0) {
      return "Coordinates cannot be negative.";
    }

    if (width < 20 || height < 20) {
      return "The table size must fit the floor plan.";
    }

    if (selectedRoom && (x + width > selectedRoom.layoutWidth || y + height > selectedRoom.layoutHeight)) {
      return "The table must remain within the dining room boundaries.";
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
    setSaveError(null);
    const previous = layoutFromTable(selectedTable);
    const next = selectedRoom
      ? clampLayoutToRoom({
          x: Number(draft.x),
          y: Number(draft.y),
          width: Number(draft.width),
          height: Number(draft.height),
        }, selectedRoom)
      : {
          x: Number(draft.x),
          y: Number(draft.y),
          width: Number(draft.width),
          height: Number(draft.height),
        };

    setTableLayouts((current) => ({ ...current, [selectedTable.id]: next }));
    updateLayoutMutation.mutate({
      tableId: selectedTable.id,
      layout: next,
      previous,
    });
  }

  function previewTableLayout(tableId: number, layout: TableLayoutDraft) {
    setTableLayouts((current) => ({ ...current, [tableId]: layout }));
  }

  function commitTableLayout(tableId: number, previous: TableLayoutDraft, next: TableLayoutDraft) {
    if (sameLayout(previous, next)) {
      return;
    }

    setValidationError(null);
    setSaveError(null);
    updateLayoutMutation.mutate({
      tableId,
      layout: next,
      previous,
    });
  }

  return (
    <ConfigShell title={t("Floor plan")}>
      <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <ConfigCard title={t("Dining room floor plan")}>
          {diningRoomsQuery.error || tablesQuery.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(diningRoomsQuery.error ?? tablesQuery.error)}
            </StatusMessage>
          ) : null}

          <div className="mb-5 grid gap-4 sm:grid-cols-2">
            <SelectField
              label={t("Dining room")}
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
              label={t("Table")}
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

          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-brand-300/20 bg-brand-500/10 px-4 py-3">
            <p className="text-sm text-brand-100">
              {t("Drag a table to move it.")}
            </p>
            <label className="inline-flex items-center gap-3 text-sm font-medium text-slate-200">
              <input
                className="h-5 w-5 rounded border-white/20 bg-slate-950"
                type="checkbox"
                checked={snapToGrid}
                onChange={(event) => setSnapToGrid(event.target.checked)}
              />
              {t("Snap to grid")}
            </label>
          </div>

          {saveError ? (
            <StatusMessage tone="error">{saveError}</StatusMessage>
          ) : null}

          {selectedRoom ? (
            <div className="overflow-auto rounded-lg border border-white/10 bg-slate-950/80 p-4">
              <div
                className="relative overflow-hidden rounded-lg border border-white/10 bg-[#0d1210]"
                style={{
                  width: selectedRoom.layoutWidth * boardScale,
                  height: selectedRoom.layoutHeight * boardScale,
                  minWidth: "280px",
                  touchAction: "none",
                  userSelect: "none",
                }}
              >
                <div className="absolute inset-0 opacity-35 [background-image:linear-gradient(rgba(255,255,255,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.08)_1px,transparent_1px)] [background-size:20px_20px]" />
                {tablesInRoom.map((table) => (
                  <DraggableTable
                    key={table.id}
                    table={table}
                    room={selectedRoom}
                    boardScale={boardScale}
                    selected={selectedTable?.id === table.id}
                    saving={updateLayoutMutation.isPending && updateLayoutMutation.variables?.tableId === table.id}
                    snapToGrid={snapToGrid}
                    onSelect={setSelectedTableId}
                    onPreview={previewTableLayout}
                    onCommit={commitTableLayout}
                  />
                ))}
              </div>
            </div>
          ) : (
            <StatusMessage tone="info">
              {t("Create a dining room and a table to edit the floor plan.")}
            </StatusMessage>
          )}
        </ConfigCard>

        <ConfigCard title={t("Position and size")}>
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
              <div className="rounded-lg border border-white/10 bg-white/5 p-4">
                <h3 className="text-lg font-semibold text-white">
                  {selectedTable.code}
                  {selectedTable.label ? ` · ${selectedTable.label}` : ""}
                </h3>
                <p className="mt-2 text-sm text-slate-400">
                  {t("Position")} {selectedTable.x},{selectedTable.y} · {t("Size")} {selectedTable.width} × {selectedTable.height}
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={`${t("Position")} X`}
                  type="number"
                  value={draft.x}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, x: event.target.value }))
                  }
                />
                <TextField
                  label={`${t("Position")} Y`}
                  type="number"
                  value={draft.y}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, y: event.target.value }))
                  }
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label={t("Width")}
                  type="number"
                  value={draft.width}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, width: event.target.value }))
                  }
                />
                <TextField
                  label={t("Height")}
                  type="number"
                  value={draft.height}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, height: event.target.value }))
                  }
                />
              </div>

              <button
                className="h-12 rounded-lg bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="button"
                disabled={updateLayoutMutation.isPending}
                onClick={applyDraft}
              >
                {updateLayoutMutation.isPending ? t("Applying...") : t("Apply changes")}
              </button>
            </div>
          ) : (
            <StatusMessage tone="info">
              {t("Select a table to edit it.")}
            </StatusMessage>
          )}
        </ConfigCard>
      </div>
    </ConfigShell>
  );
}

function DraggableTable({
  table,
  room,
  boardScale,
  selected,
  saving,
  snapToGrid,
  onSelect,
  onPreview,
  onCommit,
}: {
  table: RestaurantTableResponse;
  room: DiningRoomResponse;
  boardScale: number;
  selected: boolean;
  saving: boolean;
  snapToGrid: boolean;
  onSelect: (tableId: number) => void;
  onPreview: (tableId: number, layout: TableLayoutDraft) => void;
  onCommit: (tableId: number, previous: TableLayoutDraft, next: TableLayoutDraft) => void;
}) {
  const { t } = useI18n();
  const layout = layoutFromTable(table);
  const { isDragging, dragHandlers } = useDraggableTable({
    tableId: table.id,
    layout,
    boardScale,
    bounds: {
      width: room.layoutWidth,
      height: room.layoutHeight,
    },
    snapToGrid,
    onSelect,
    onPreview,
    onCommit,
  });

  return (
    <button
      type="button"
      aria-label={`${t("Move table")} ${table.code}`}
      title={t("Drag to move")}
      className={[
        "absolute z-10 touch-none select-none rounded-lg border text-xs font-semibold transition-transform duration-100",
        "cursor-grab active:cursor-grabbing",
        selected
          ? "border-brand-300 bg-brand-500 text-slate-950"
          : "border-white/10 bg-slate-900/90 text-white hover:border-brand-400/40",
        isDragging ? "z-20 scale-105 shadow-2xl shadow-brand-500/30 ring-2 ring-brand-200" : "shadow-lg shadow-black/25",
        saving ? "opacity-70" : "",
      ].join(" ")}
      style={{
        left: layout.x * boardScale,
        top: layout.y * boardScale,
        width: Math.max(56, layout.width * boardScale),
        height: Math.max(48, layout.height * boardScale),
      }}
      {...dragHandlers}
    >
      <span className="grid gap-1">
        <span>{table.code}</span>
        <span className="text-[0.65rem] opacity-70">
          {saving ? t("Saving...") : `${table.minCapacity}-${table.maxCapacity} ${t("guests")}`}
        </span>
      </span>
    </button>
  );
}
