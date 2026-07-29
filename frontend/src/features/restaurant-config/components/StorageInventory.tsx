import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as configApi from "@/features/restaurant-config/api/configApi";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import {
  CheckboxField,
  SelectField,
  TextAreaField,
  TextField,
} from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import type {
  StorageResourceResponse,
  StorageResourceType,
} from "@/features/restaurant-config/types";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useI18n } from "@/features/i18n/I18nProvider";

const resourceTypes: Array<{ value: StorageResourceType; label: string }> = [
  { value: "EXTRA_CHAIR", label: "Silla extra" },
  { value: "EXTRA_TABLE", label: "Mesa extra" },
  { value: "FOLDING_TABLE", label: "Mesa plegable" },
  { value: "HIGH_CHAIR", label: "Trona" },
  { value: "TABLE_EXTENSION", label: "Extension de mesa" },
  { value: "BENCH", label: "Banco" },
  { value: "STORAGE_TABLE", label: "Mesa de almacen" },
  { value: "OTHER", label: "Otro" },
];

type ActiveFilter = "ALL" | "ACTIVE" | "INACTIVE";

const emptyForm = {
  resourceType: "EXTRA_CHAIR" as StorageResourceType,
  name: "",
  quantity: "0",
  capacityPerUnit: "0",
  setupTimeMinutes: "0",
  notes: "",
  active: true,
};

function typeLabel(type: StorageResourceType) {
  return resourceTypes.find((option) => option.value === type)?.label ?? type;
}

export function StorageInventory({
  activeRestaurantId,
}: {
  activeRestaurantId: number | null;
}) {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const [resourceTypeFilter, setResourceTypeFilter] = useState<StorageResourceType | "ALL">("ALL");
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>("ALL");
  const [selected, setSelected] = useState<StorageResourceResponse | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [validationError, setValidationError] = useState<string | null>(null);

  const allResourcesQuery = useQuery({
    queryKey: ["storageResources", activeRestaurantId, "summary"],
    queryFn: () => configApi.getStorageResources(activeRestaurantId!),
    enabled: activeRestaurantId !== null,
  });

  const hasFilters = resourceTypeFilter !== "ALL" || activeFilter !== "ALL";
  const filteredResourcesQuery = useQuery({
    queryKey: ["storageResources", activeRestaurantId, "filtered", resourceTypeFilter, activeFilter],
    queryFn: () =>
      configApi.getStorageResources(activeRestaurantId!, {
        resourceType: resourceTypeFilter === "ALL" ? undefined : resourceTypeFilter,
        active: activeFilter === "ALL" ? undefined : activeFilter === "ACTIVE",
      }),
    enabled: activeRestaurantId !== null && hasFilters,
  });

  const displayedResources = hasFilters ? filteredResourcesQuery.data : allResourcesQuery.data;
  const listQuery = hasFilters ? filteredResourcesQuery : allResourcesQuery;

  useEffect(() => {
    if (!selected) {
      setForm(emptyForm);
      return;
    }

    setForm({
      resourceType: selected.resourceType,
      name: selected.name,
      quantity: String(selected.quantity),
      capacityPerUnit: String(selected.capacityPerUnit),
      setupTimeMinutes: String(selected.setupTimeMinutes),
      notes: selected.notes ?? "",
      active: selected.active,
    });
  }, [selected]);

  const summary = useMemo(() => {
    const resources = allResourcesQuery.data ?? [];
    const activeResources = resources.filter((resource) => resource.active);
    const tableTypes = new Set<StorageResourceType>([
      "EXTRA_TABLE",
      "FOLDING_TABLE",
      "STORAGE_TABLE",
    ]);

    return {
      extraChairs: activeResources
        .filter((resource) => resource.resourceType === "EXTRA_CHAIR")
        .reduce((total, resource) => total + resource.quantity, 0),
      storageTables: activeResources
        .filter((resource) => tableTypes.has(resource.resourceType))
        .reduce((total, resource) => total + resource.quantity, 0),
      activeResources: activeResources.length,
      inactiveResources: resources.length - activeResources.length,
    };
  }, [allResourcesQuery.data]);

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["storageResources", activeRestaurantId] });

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        resourceType: form.resourceType,
        name: form.name.trim(),
        quantity: Number(form.quantity),
        capacityPerUnit: Number(form.capacityPerUnit),
        setupTimeMinutes: Number(form.setupTimeMinutes),
        active: form.active,
        notes: form.notes.trim() || null,
      };

      return selected
        ? configApi.updateStorageResource(activeRestaurantId!, selected.id, payload)
        : configApi.createStorageResource(activeRestaurantId!, payload);
    },
    onSuccess: async () => {
      setSelected(null);
      setForm(emptyForm);
      await refresh();
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: (resource: StorageResourceResponse) =>
      configApi.updateStorageResource(activeRestaurantId!, resource.id, {
        active: !resource.active,
      }),
    onSuccess: async (_, resource) => {
      if (selected?.id === resource.id) {
        setSelected(null);
      }
      await refresh();
    },
  });

  function validateForm() {
    if (!form.name.trim()) {
      return t("El nombre es obligatorio.");
    }

    const numericValues = [form.quantity, form.capacityPerUnit, form.setupTimeMinutes];
    if (numericValues.some((value) => value === "" || !Number.isInteger(Number(value)) || Number(value) < 0)) {
      return t("Cantidad, capacidad y preparacion deben ser numeros enteros positivos.");
    }

    return null;
  }

  const mutationError = saveMutation.error ?? toggleActiveMutation.error;

  return (
    <ConfigCard title={t("Inventario")}>

      <dl className="mb-5 grid border-y border-white/10 sm:grid-cols-2 xl:grid-cols-4">
        {[
          [t("Sillas extra"), summary.extraChairs],
          [t("Mesas de almacen"), summary.storageTables],
          [t("Recursos activos"), summary.activeResources],
          [t("Recursos inactivos"), summary.inactiveResources],
        ].map(([label, value], index) => (
          <div
            key={label}
            className={[
              "min-w-0 px-3 py-4",
              index > 0 ? "border-t border-white/10 sm:border-t-0 sm:border-l" : "",
              index === 2 ? "sm:border-l-0 xl:border-l" : "",
            ].join(" ")}
          >
            <dt className="text-xs font-medium uppercase text-slate-500">{label}</dt>
            <dd className="mt-1 text-2xl font-semibold text-white">{value}</dd>
          </div>
        ))}
      </dl>

      <div className="mb-5 grid gap-4 sm:grid-cols-2">
        <SelectField
          label={t("Tipo de recurso")}
          value={resourceTypeFilter}
          onChange={(event) => setResourceTypeFilter(event.target.value as StorageResourceType | "ALL")}
        >
          <option value="ALL">{t("Todos los tipos")}</option>
          {resourceTypes.map((option) => (
            <option key={option.value} value={option.value}>{t(option.label)}</option>
          ))}
        </SelectField>
        <SelectField
          label={t("Estado")}
          value={activeFilter}
          onChange={(event) => setActiveFilter(event.target.value as ActiveFilter)}
        >
          <option value="ALL">{t("Todos los recursos")}</option>
          <option value="ACTIVE">{t("Activo")}</option>
          <option value="INACTIVE">{t("Inactivo")}</option>
        </SelectField>
      </div>

      {listQuery.isLoading ? <StatusMessage tone="info">{t("Cargando inventario...")}</StatusMessage> : null}
      {listQuery.error ? <StatusMessage tone="error">{getErrorMessage(listQuery.error)}</StatusMessage> : null}
      {mutationError ? <StatusMessage tone="error">{getErrorMessage(mutationError)}</StatusMessage> : null}

      {displayedResources && displayedResources.length > 0 ? (
        <div className="overflow-x-auto border-y border-white/10">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-3 font-medium">{t("Nombre")}</th>
                <th className="px-3 py-3 font-medium">{t("Tipo")}</th>
                <th className="px-3 py-3 text-right font-medium">{t("Cantidad")}</th>
                <th className="px-3 py-3 text-right font-medium">{t("Capacidad por unidad")}</th>
                <th className="px-3 py-3 text-right font-medium">{t("Preparacion")}</th>
                <th className="px-3 py-3 font-medium">{t("Activo")}</th>
                <th className="px-3 py-3 text-right font-medium">{t("Acciones")}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {displayedResources.map((resource) => (
                <tr key={resource.id} className={selected?.id === resource.id ? "bg-brand-500/10" : ""}>
                  <td className="max-w-52 px-3 py-4">
                    <p className="font-medium text-white">{resource.name}</p>
                    {resource.notes ? <p className="mt-1 truncate text-xs text-slate-500">{resource.notes}</p> : null}
                  </td>
                  <td className="px-3 py-4 text-slate-300">{t(typeLabel(resource.resourceType))}</td>
                  <td className="px-3 py-4 text-right tabular-nums text-slate-300">{resource.quantity}</td>
                  <td className="px-3 py-4 text-right tabular-nums text-slate-300">{resource.capacityPerUnit}</td>
                  <td className="px-3 py-4 text-right tabular-nums text-slate-300">{resource.setupTimeMinutes} min</td>
                  <td className="px-3 py-4 text-slate-300">
                    <span className={resource.active ? "text-emerald-300" : "text-slate-500"}>
                      {resource.active ? t("Activo") : t("Inactivo")}
                    </span>
                  </td>
                  <td className="px-3 py-4">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="h-10 rounded-lg border border-white/10 px-3 font-medium text-white hover:border-brand-400/50"
                        onClick={() => setSelected(resource)}
                      >
                        {t("Editar")}
                      </button>
                      <button
                        type="button"
                        className={[
                          "h-10 rounded-lg border px-3 font-medium disabled:opacity-60",
                          resource.active
                            ? "border-rose-400/30 text-rose-200 hover:border-rose-400/60"
                            : "border-emerald-400/30 text-emerald-200 hover:border-emerald-400/60",
                        ].join(" ")}
                        disabled={toggleActiveMutation.isPending}
                        onClick={() => toggleActiveMutation.mutate(resource)}
                      >
                        {resource.active ? t("Desactivar") : t("Reactivar")}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {displayedResources?.length === 0 ? (
        <StatusMessage tone="info">{t("No hay recursos con estos filtros.")}</StatusMessage>
      ) : null}

      <section className="mt-6 border-t border-white/10 pt-5">
        <h3 className="mb-4 text-base font-semibold text-white">
          {selected ? `${t("Editar")} ${selected.name}` : t("Añadir recurso")}
        </h3>
        {validationError ? <StatusMessage tone="error">{validationError}</StatusMessage> : null}
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            const nextError = validateForm();
            if (nextError) {
              setValidationError(nextError);
              return;
            }
            setValidationError(null);
            saveMutation.mutate();
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              label={t("Tipo de recurso")}
              value={form.resourceType}
              onChange={(event) => setForm((current) => ({
                ...current,
                resourceType: event.target.value as StorageResourceType,
              }))}
            >
              {resourceTypes.map((option) => (
                <option key={option.value} value={option.value}>{t(option.label)}</option>
              ))}
            </SelectField>
            <TextField
              label={t("Nombre")}
              value={form.name}
              maxLength={160}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              required
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField
              label={t("Cantidad")}
              type="number"
              min={0}
              step={1}
              value={form.quantity}
              onChange={(event) => setForm((current) => ({ ...current, quantity: event.target.value }))}
              required
            />
            <TextField
              label={t("Capacidad por unidad")}
              type="number"
              min={0}
              step={1}
              value={form.capacityPerUnit}
              onChange={(event) => setForm((current) => ({ ...current, capacityPerUnit: event.target.value }))}
              required
            />
            <TextField
              label={`${t("Preparacion")} (${t("Minutos").toLowerCase()})`}
              type="number"
              min={0}
              step={1}
              value={form.setupTimeMinutes}
              onChange={(event) => setForm((current) => ({ ...current, setupTimeMinutes: event.target.value }))}
              required
            />
          </div>
          <TextAreaField
            label={t("Notas")}
            value={form.notes}
            maxLength={2000}
            onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
            placeholder={t("Ubicacion o instrucciones")}
          />
          <CheckboxField
            label={t("Recurso activo")}
            checked={form.active}
            onChange={(active) => setForm((current) => ({ ...current, active }))}
          />
          <div className="flex flex-wrap justify-end gap-3">
            {selected ? (
              <button
                type="button"
                className="h-11 rounded-lg border border-white/10 px-4 font-medium text-white hover:border-white/25"
                onClick={() => {
                  setSelected(null);
                  setValidationError(null);
                }}
              >
                {t("Cancelar")}
              </button>
            ) : null}
            <button
              type="submit"
              className="h-11 rounded-lg bg-brand-500 px-5 font-semibold text-slate-950 hover:bg-brand-400 disabled:opacity-60"
              disabled={saveMutation.isPending}
            >
              {saveMutation.isPending ? t("Guardando...") : selected ? t("Guardar cambios") : t("Crear recurso")}
            </button>
          </div>
        </form>
      </section>
    </ConfigCard>
  );
}
