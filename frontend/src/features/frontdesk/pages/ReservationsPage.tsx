import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { CalendarSearch, Plus } from "lucide-react";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
import type { SearchReservationsParams } from "@/features/frontdesk/api/frontdeskApi";
import { OperationsShell } from "@/features/frontdesk/components/OperationsShell";
import { ReservationDetailPanel } from "@/features/frontdesk/components/ReservationDetailPanel";
import { ReservationForm } from "@/features/frontdesk/components/ReservationForm";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import type { ReservationResponse } from "@/features/frontdesk/types";
import {
  formatReservationCustomerName,
  normalizeTimeForInput,
  todayDateValue,
} from "@/features/frontdesk/utils/frontdeskUtils";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";
import { ConfigCard } from "@/features/restaurant-config/components/ConfigCard";
import { SelectField, TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/features/i18n/I18nProvider";

export function ReservationsPage() {
  const queryClient = useQueryClient();
  const { t } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const createReservationSectionRef = useRef<HTMLDivElement | null>(null);
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedDate, setSelectedDate] = useState(searchParams.get("date") ?? todayDateValue());
  const [selectedReservationId, setSelectedReservationId] = useState<number | null>(null);
  const [mode, setMode] = useState<"create" | "detail">(
    searchParams.get("mode") === "new" ? "create" : "detail",
  );
  const [searchMode, setSearchMode] = useState(false);
  const [searchFilters, setSearchFilters] = useState<SearchReservationsParams>({
    customerQuery: "",
    status: "",
    dateFrom: "",
    dateTo: "",
  });

  const activeRoles =
    session.restaurants.find((restaurant) => restaurant.id === activeRestaurantId)?.roles ?? [];
  const canManageReservations = activeRoles.some((role) =>
    ["PLATFORM_ADMIN", "RESTAURANT_OWNER", "MANAGER"].includes(role),
  );
  const canOperateReservations =
    canManageReservations || activeRoles.includes("WAITER");

  const reservationsQuery = useQuery({
    queryKey: ["reservations", activeRestaurantId, selectedDate],
    queryFn: () => frontdeskApi.getReservations(activeRestaurantId!, selectedDate),
    enabled: activeRestaurantId !== null && !searchMode,
  });

  const searchQuery = useQuery({
    queryKey: ["reservations-search", activeRestaurantId, searchFilters],
    queryFn: () => frontdeskApi.searchReservations(activeRestaurantId!, searchFilters),
    enabled: activeRestaurantId !== null && searchMode,
  });

  const reservations = useMemo(
    () => (searchMode ? searchQuery.data : reservationsQuery.data) ?? [],
    [searchMode, searchQuery.data, reservationsQuery.data],
  );

  const selectedReservation =
    mode === "detail"
      ? reservations.find((reservation) => reservation.id === selectedReservationId) ?? null
      : null;

  useEffect(() => {
    const queryDate = searchParams.get("date");
    if (queryDate && queryDate !== selectedDate) {
      setSelectedDate(queryDate);
    }

    if (searchParams.get("mode") === "new") {
      setMode("create");
      setSelectedReservationId(null);
    }
  }, [searchParams, selectedDate]);

  useEffect(() => {
    if (mode === "create") {
      return;
    }

    if (!selectedReservationId && reservations.length > 0) {
      setSelectedReservationId(reservations[0].id);
      return;
    }

    if (
      selectedReservationId !== null &&
      reservations.every((reservation) => reservation.id !== selectedReservationId)
    ) {
      setSelectedReservationId(null);
    }
  }, [mode, reservations, selectedReservationId]);

  useEffect(() => {
    if (mode !== "create") {
      return;
    }

    window.requestAnimationFrame(() => {
      createReservationSectionRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    });
  }, [mode]);

  async function refreshReservations() {
    if (searchMode) {
      await queryClient.invalidateQueries({
        queryKey: ["reservations-search", activeRestaurantId],
      });
    } else {
      await queryClient.invalidateQueries({
        queryKey: ["reservations", activeRestaurantId, selectedDate],
      });
    }
  }

  function handleReservationChanged(nextReservation: ReservationResponse) {
    queryClient.setQueryData<ReservationResponse[] | undefined>(
      ["reservations", activeRestaurantId, selectedDate],
      (current) =>
        (current ?? []).map((reservation) =>
          reservation.id === nextReservation.id ? nextReservation : reservation,
        ),
    );
  }

  return (
    <OperationsShell title={t("Reservas")}>
      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <div className="grid gap-6">
          <ConfigCard title={searchMode ? t("Buscar reservas") : t("Reservas del dia")}>
            <div className="mb-5 grid gap-4 sm:grid-cols-[220px_auto] sm:items-end">
              {searchMode ? (
                <TextField
                  label={t("Buscar cliente")}
                  placeholder={t("Nombre o apellidos")}
                  value={searchFilters.customerQuery ?? ""}
                  onChange={(event) =>
                    setSearchFilters((prev) => ({ ...prev, customerQuery: event.target.value }))
                  }
                />
              ) : (
                <TextField
                  label={t("Fecha")}
                  type="date"
                  value={selectedDate}
                  onChange={(event) => {
                    const nextDate = event.target.value;
                    setSelectedDate(nextDate);
                    setSearchParams((current) => {
                      const next = new URLSearchParams(current);
                      next.set("date", nextDate);
                      if (mode === "create") {
                        next.set("mode", "new");
                      }
                      return next;
                    });
                  }}
                />
              )}
              <div className="flex flex-wrap gap-3">
                <button
                  className="h-12 rounded-lg border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => {
                    setSearchMode(!searchMode);
                    setSelectedReservationId(null);
                  }}
                >
                  <span className="inline-flex items-center gap-2">
                    <CalendarSearch className="h-4 w-4" />
                    {searchMode ? t("Ver por fecha") : t("Buscar")}
                  </span>
                </button>
                <button
                  className="h-12 rounded-lg border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => {
                    setMode("create");
                    setSelectedReservationId(null);
                    setSearchParams((current) => {
                      const next = new URLSearchParams(current);
                      next.set("mode", "new");
                      next.set("date", selectedDate);
                      return next;
                    });
                  }}
                >
                  <span className="inline-flex items-center gap-2">
                    <Plus className="h-4 w-4" />
                    {t("Nueva reserva")}
                  </span>
                </button>
              </div>
            </div>

            {searchMode ? (
              <div className="mb-5 grid gap-4 sm:grid-cols-3">
                <SelectField
                  label={t("Estado")}
                  value={searchFilters.status ?? ""}
                  onChange={(event) =>
                    setSearchFilters((prev) => ({ ...prev, status: event.target.value }))
                  }
                >
                  <option value="">{t("Todos")}</option>
                  <option value="PENDING">{t("Pendiente")}</option>
                  <option value="CONFIRMED">{t("Confirmada")}</option>
                  <option value="SEATED">{t("Sentada")}</option>
                  <option value="COMPLETED">{t("Completada")}</option>
                  <option value="CANCELLED">{t("Cancelada")}</option>
                  <option value="NO_SHOW">{t("No presentado")}</option>
                </SelectField>
                <TextField
                  label={t("Desde")}
                  type="date"
                  value={searchFilters.dateFrom ?? ""}
                  onChange={(event) =>
                    setSearchFilters((prev) => ({ ...prev, dateFrom: event.target.value }))
                  }
                />
                <TextField
                  label={t("Hasta")}
                  type="date"
                  value={searchFilters.dateTo ?? ""}
                  onChange={(event) =>
                    setSearchFilters((prev) => ({ ...prev, dateTo: event.target.value }))
                  }
                />
              </div>
            ) : null}

            {reservationsQuery.isLoading || searchQuery.isLoading ? (
              <StatusMessage tone="info">{t("Cargando reservas...")}</StatusMessage>
            ) : null}
            {reservationsQuery.error || searchQuery.error ? (
              <StatusMessage tone="error">
                {getErrorMessage(reservationsQuery.error ?? searchQuery.error)}
              </StatusMessage>
            ) : null}

            <div className="grid gap-3">
              {reservations.map((reservation) => (
                <button
                  key={reservation.id}
                  className={[
                    "w-full rounded-lg border p-4 text-left transition",
                    selectedReservationId === reservation.id
                      ? "border-brand-400/60 bg-brand-500/10"
                      : "border-white/10 bg-white/5 hover:border-brand-400/30",
                  ].join(" ")}
                  type="button"
                  onClick={() => {
                    setMode("detail");
                    setSelectedReservationId(reservation.id);
                    setSearchParams((current) => {
                      const next = new URLSearchParams(current);
                      next.delete("mode");
                      next.set("date", selectedDate);
                      return next;
                    });
                  }}
                >
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-3">
                        <h3 className="text-lg font-semibold text-white">
                          {formatReservationCustomerName(reservation)}
                        </h3>
                        <StatusPill status={reservation.status} />
                      </div>
                      <p className="mt-2 text-sm text-slate-300">
                        {normalizeTimeForInput(reservation.startTime)}
                        {reservation.endTime
                          ? ` - ${normalizeTimeForInput(reservation.endTime)}`
                          : ""}
                        {` · ${reservation.partySize} ${t("personas")} · ${reservation.channel}`}
                      </p>
                      {reservation.specialRequests ? (
                        <p className="mt-2 line-clamp-2 text-sm text-slate-500">
                          {reservation.specialRequests}
                        </p>
                      ) : null}
                    </div>
                  </div>
                </button>
              ))}

              {reservations.length === 0 ? (
                <StatusMessage tone="info">
                  {t("No hay reservas para la fecha seleccionada.")}
                </StatusMessage>
              ) : null}
            </div>
          </ConfigCard>
        </div>

        <div ref={createReservationSectionRef}>
          <ConfigCard
            title={selectedReservation ? t("Detalle de reserva") : t("Crear reserva manual")}
          >
            {selectedReservation ? (
              <ReservationDetailPanel
                reservation={selectedReservation}
                canOperateReservations={canOperateReservations}
                onChanged={(nextReservation) => {
                  handleReservationChanged(nextReservation);
                  void refreshReservations();
                }}
              />
            ) : (
              <ReservationForm
                canManageReservations={canManageReservations}
                initialDate={selectedDate}
                onCreated={(reservationId) => {
                  setMode("detail");
                  void refreshReservations();
                  setSelectedReservationId(reservationId);
                  setSearchParams((current) => {
                    const next = new URLSearchParams(current);
                    next.delete("mode");
                    next.set("date", selectedDate);
                    return next;
                  });
                }}
              />
            )}
          </ConfigCard>
        </div>
      </div>
    </OperationsShell>
  );
}
