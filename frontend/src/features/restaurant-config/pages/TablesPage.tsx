import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { StorageInventory } from "@/features/restaurant-config/components/StorageInventory";
import {
  CheckboxField,
  SelectField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import type { RestaurantTableResponse } from "@/features/restaurant-config/types";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { Pencil, Power } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

const emptyForm = {
  diningRoomId: "",
  tableType: "Fixed" as RestaurantTableResponse["tableType"],
  code: "",
  label: "",
  minCapacity: "2",
  maxCapacity: "4",
  shape: "Rectangle",
  x: "100",
  y: "100",
  width: "120",
  height: "80",
  active: true,
};

export function TablesPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
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
      diningRoomId: selected.diningRoomId === null ? "" : String(selected.diningRoomId),
      tableType: selected.tableType,
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
        diningRoomId: form.tableType === "Storage" ? null : Number(form.diningRoomId),
        tableType: form.tableType,
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
        diningRoomId: form.tableType === "Storage" ? null : Number(form.diningRoomId),
        tableType: form.tableType,
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

      if (form.tableType !== "Storage") {
        await configApi.updateTableLayout(activeRestaurantId!, selected.id, {
          x: Number(form.x),
          y: Number(form.y),
          width: Number(form.width),
          height: Number(form.height),
        });
      }
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
    if (form.tableType !== "Storage" && !form.diningRoomId) {
      return t("Select a dining room for the table.");
    }

    if (!form.code.trim()) {
      return t("Table code is required.");
    }

    if (Number(form.minCapacity) <= 0 || Number(form.maxCapacity) <= 0) {
      return t("Capacities must be greater than zero.");
    }

    if (Number(form.minCapacity) > Number(form.maxCapacity)) {
      return t("Minimum capacity cannot exceed maximum capacity.");
    }

    if (
      Number(form.x) < 0 ||
      Number(form.y) < 0 ||
      Number(form.width) < 20 ||
      Number(form.height) < 20
    ) {
      return t("Review the table position and size.");
    }

    if (form.tableType === "Storage" && form.active) {
      return null;
    }

    return null;
  }

  return (
    <ConfigShell title={t("Tables")}>
      <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <ConfigCard title={t("Configured tables")}>
          {tablesQuery.isLoading ? (
            <StatusMessage tone="info">{t("Loading tables...")}</StatusMessage>
          ) : null}
          {tablesQuery.error ? (
            <StatusMessage tone="error">{getErrorMessage(tablesQuery.error)}</StatusMessage>
          ) : null}

          <div className="grid gap-3">
            {tablesQuery.data?.map((table) => (
              <article
                key={table.id}
                className={[
                  "rounded-lg border p-4 transition",
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
                      {table.tableType === "Storage"
                        ? t("Storage")
                        : `${t("Dining room")} ${roomsById.get(table.diningRoomId ?? 0) ?? table.diningRoomId}`} ·{" "}
                      {table.minCapacity}-{table.maxCapacity} {t("guests")} · {t(table.shape)}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      {t("Type")} {t(table.tableType)} · {t("Position")} {table.x},{table.y} · {table.width} × {table.height} ·{" "}
                      {table.active ? t("Active") : t("Inactive")}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <button
                      className="h-11 rounded-lg border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                      type="button"
                      onClick={() => setSelected(table)}
                    >
                      <span className="inline-flex items-center gap-2">
                        <Pencil className="h-4 w-4" />
                        {t("Edit")}
                      </span>
                    </button>
                    <button
                      className="h-11 rounded-lg border border-white/10 bg-rose-500/10 px-4 text-sm font-medium text-rose-100 transition hover:border-rose-400/40"
                      type="button"
                      onClick={() => deactivateMutation.mutate(table.id)}
                    >
                      <span className="inline-flex items-center gap-2">
                        <Power className="h-4 w-4" />
                        {t("Deactivate")}
                      </span>
                    </button>
                  </div>
                </div>
              </article>
            ))}

            {tablesQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                {t("No tables have been configured.")}
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard title={selected ? t("Edit table") : t("Create table")}>
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
              label={t("Dining room")}
              value={form.diningRoomId}
              disabled={form.tableType === "Storage"}
              onChange={(event) =>
                setForm((current) => ({ ...current, diningRoomId: event.target.value }))
              }
            >
              <option value="" disabled>
                {t("Select a dining room")}
              </option>
              {(diningRoomsQuery.data ?? []).map((room) => (
                <option key={room.id} value={room.id}>
                  {room.name}
                </option>
              ))}
            </SelectField>
            <SelectField
              label={t("Table type")}
              value={form.tableType}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  tableType: event.target.value as RestaurantTableResponse["tableType"],
                  diningRoomId: event.target.value === "Storage" ? "" : current.diningRoomId,
                }))
              }
            >
              <option value="Fixed">{t("Fixed")}</option>
              <option value="Movable">{t("Movable")}</option>
              <option value="Storage">{t("Storage")}</option>
              <option value="Temporary">{t("Temporary")}</option>
            </SelectField>
            {form.tableType === "Storage" ? (
              <StatusMessage tone="info">
                {t("Storage tables only appear in inventory.")}
              </StatusMessage>
            ) : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t("Code")}
                value={form.code}
                onChange={(event) =>
                  setForm((current) => ({ ...current, code: event.target.value }))
                }
                required
              />
              <TextField
                label={t("Label")}
                value={form.label}
                onChange={(event) =>
                  setForm((current) => ({ ...current, label: event.target.value }))
                }
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <TextField
                label={t("Minimum capacity")}
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
                label={t("Maximum capacity")}
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
                label={t("Shape")}
                value={form.shape}
                onChange={(event) =>
                  setForm((current) => ({ ...current, shape: event.target.value }))
                }
              >
                <option value="Rectangle">{t("Rectangle")}</option>
                <option value="Round">{t("Round")}</option>
                <option value="Square">{t("Square")}</option>
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
                label={t("Width")}
                type="number"
                min={20}
                value={form.width}
                onChange={(event) =>
                  setForm((current) => ({ ...current, width: event.target.value }))
                }
                required
              />
              <TextField
                label={t("Height")}
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
              label={t("Active table")}
              checked={form.active}
              onChange={(checked) =>
                setForm((current) => ({ ...current, active: checked }))
              }
            />

            <div className="flex flex-wrap justify-end gap-3">
              {selected ? (
                <button
                  className="h-12 rounded-lg border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-white/20"
                  type="button"
                  onClick={() => {
                    setSelected(null);
                    setValidationError(null);
                  }}
                >
                  {t("Cancel")}
                </button>
              ) : null}
              <button
                className="h-12 rounded-lg bg-brand-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-brand-400 disabled:opacity-60"
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {selected
                  ? updateMutation.isPending
                    ? t("Saving...")
                    : t("Save table")
                  : createMutation.isPending
                    ? t("Creating...")
                    : t("Create table")}
              </button>
            </div>
          </form>
        </ConfigCard>

        <StorageInventory
          activeRestaurantId={activeRestaurantId}
        />
      </div>
    </ConfigShell>
  );
}
