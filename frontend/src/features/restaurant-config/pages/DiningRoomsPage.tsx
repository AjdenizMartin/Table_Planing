import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { ConfigShell } from "@/features/restaurant-config/components/ConfigShell";
import { CheckboxField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import type { DiningRoomResponse } from "@/features/restaurant-config/types";
import { Pencil, Power } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

const emptyForm = {
  name: "",
  priority: "1",
  accessible: true,
  active: true,
  layoutWidth: "1200",
  layoutHeight: "800",
};

export function DiningRoomsPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selected, setSelected] = useState<DiningRoomResponse | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);

  const diningRoomsQuery = useQuery({
    queryKey: ["diningRooms", activeRestaurantId],
    queryFn: () => configApi.getDiningRooms(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  useEffect(() => {
    if (!selected) {
      setForm(emptyForm);
      return;
    }

    setForm({
      name: selected.name,
      priority: String(selected.priority),
      accessible: selected.accessible,
      active: selected.active,
      layoutWidth: String(selected.layoutWidth),
      layoutHeight: String(selected.layoutHeight),
    });
  }, [selected]);

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["diningRooms", activeRestaurantId] });

  const createMutation = useMutation({
    mutationFn: () =>
      configApi.createDiningRoom(activeRestaurantId!, {
        name: form.name,
        priority: Number(form.priority),
        accessible: form.accessible,
        active: form.active,
        layoutWidth: Number(form.layoutWidth),
        layoutHeight: Number(form.layoutHeight),
      }),
    onSuccess: async () => {
      setForm(emptyForm);
      await refresh();
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      configApi.updateDiningRoom(activeRestaurantId!, selected!.id, {
        name: form.name,
        priority: Number(form.priority),
        accessible: form.accessible,
        active: form.active,
        layoutWidth: Number(form.layoutWidth),
        layoutHeight: Number(form.layoutHeight),
      }),
    onSuccess: async () => {
      setSelected(null);
      await refresh();
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (diningRoomId: number) =>
      configApi.deactivateDiningRoom(activeRestaurantId!, diningRoomId),
    onSuccess: async () => {
      if (selected) {
        setSelected(null);
      }
      await refresh();
    },
  });

  const feedbackError =
    createMutation.error ?? updateMutation.error ?? deactivateMutation.error;

  function validateForm() {
    if (!form.name.trim()) {
      return t("Dining room name is required.");
    }

    if (Number(form.priority) <= 0) {
      return t("Priority must be greater than zero.");
    }

    if (Number(form.layoutWidth) < 100 || Number(form.layoutHeight) < 100) {
      return t("The floor plan must have a valid width and height.");
    }

    return null;
  }

  return (
    <ConfigShell title={t("Dining rooms")}>
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <ConfigCard title={t("Dining rooms")}>
          {diningRoomsQuery.isLoading ? (
            <StatusMessage tone="info">{t("Loading dining rooms...")}</StatusMessage>
          ) : null}
          {diningRoomsQuery.error ? (
            <StatusMessage tone="error">
              {getErrorMessage(diningRoomsQuery.error)}
            </StatusMessage>
          ) : null}

          <div className="grid gap-3">
            {diningRoomsQuery.data?.map((room) => (
              <article
                key={room.id}
                className={[
                  "rounded-lg border p-4 transition",
                  selected?.id === room.id
                    ? "border-brand-400/60 bg-brand-500/10"
                    : "border-white/10 bg-white/5",
                ].join(" ")}
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-white">{room.name}</h3>
                    <p className="mt-2 text-sm text-slate-400">
                      {t("Priority")} {room.priority} ·{" "}
                      {room.accessible ? t("Accessible") : t("Not accessible")} ·{" "}
                      {room.active ? t("Active") : t("Inactive")}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      {t("Floor plan")} {room.layoutWidth} × {room.layoutHeight}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <button
                      className="h-11 rounded-lg border border-white/10 bg-slate-900/70 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                      type="button"
                      onClick={() => setSelected(room)}
                    >
                      <span className="inline-flex items-center gap-2">
                        <Pencil className="h-4 w-4" />
                        {t("Edit")}
                      </span>
                    </button>
                    <button
                      className="h-11 rounded-lg border border-white/10 bg-rose-500/10 px-4 text-sm font-medium text-rose-100 transition hover:border-rose-400/40"
                      type="button"
                      onClick={() => deactivateMutation.mutate(room.id)}
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

            {diningRoomsQuery.data?.length === 0 ? (
              <StatusMessage tone="info">
                {t("No dining rooms have been configured.")}
              </StatusMessage>
            ) : null}
          </div>
        </ConfigCard>

        <ConfigCard title={selected ? t("Edit dining room") : t("Create dining room")}>
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
              label={t("First name")}
              value={form.name}
              onChange={(event) =>
                setForm((current) => ({ ...current, name: event.target.value }))
              }
              required
            />
            <TextField
              label={t("Priority")}
              type="number"
              min={1}
              value={form.priority}
              onChange={(event) =>
                setForm((current) => ({ ...current, priority: event.target.value }))
              }
              required
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={t("Floor plan width")}
                type="number"
                min={100}
                value={form.layoutWidth}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    layoutWidth: event.target.value,
                  }))
                }
                required
              />
              <TextField
                label={t("Floor plan height")}
                type="number"
                min={100}
                value={form.layoutHeight}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    layoutHeight: event.target.value,
                  }))
                }
                required
              />
            </div>
            <CheckboxField
              label={t("Accessible dining room")}
              checked={form.accessible}
              onChange={(checked) =>
                setForm((current) => ({ ...current, accessible: checked }))
              }
            />
            <CheckboxField
              label={t("Active dining room")}
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
                    : t("Save dining room")
                  : createMutation.isPending
                    ? t("Creating...")
                    : t("Create dining room")}
              </button>
            </div>
          </form>
        </ConfigCard>
      </div>
    </ConfigShell>
  );
}
