import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import * as frontdeskApi from "@/features/frontdesk/api/frontdeskApi";
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
import { TextField } from "@/features/restaurant-config/components/Field";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { getErrorMessage } from "@/features/restaurant-config/utils/errorMessage";
import { useAuth } from "@/features/auth/context/AuthContext";

export function ReservationsPage() {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const { activeRestaurantId } = useActiveRestaurant();
  const [selectedDate, setSelectedDate] = useState(todayDateValue());
  const [selectedReservationId, setSelectedReservationId] = useState<number | null>(null);

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
    enabled: activeRestaurantId !== null,
  });

  const reservations = useMemo(
    () => reservationsQuery.data ?? [],
    [reservationsQuery.data],
  );

  const selectedReservation =
    reservations.find((reservation) => reservation.id === selectedReservationId) ?? null;

  useEffect(() => {
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
  }, [reservations, selectedReservationId]);

  async function refreshReservations() {
    await queryClient.invalidateQueries({
      queryKey: ["reservations", activeRestaurantId, selectedDate],
    });
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
    <OperationsShell
      title="Agenda manual de reservas"
      description="Crea reservas manuales, consulta el servicio por fecha y actualiza estados operativos desde una sola vista."
    >
      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <div className="grid gap-6">
          <ConfigCard title="Reservas del dia">
            <div className="mb-5 grid gap-4 sm:grid-cols-[220px_auto] sm:items-end">
              <TextField
                label="Fecha"
                type="date"
                value={selectedDate}
                onChange={(event) => setSelectedDate(event.target.value)}
              />
              <div className="flex gap-3">
                <button
                  className="h-12 rounded-2xl border border-white/10 bg-white/5 px-5 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
                  type="button"
                  onClick={() => setSelectedReservationId(null)}
                >
                  Nueva reserva
                </button>
              </div>
            </div>

            {reservationsQuery.isLoading ? (
              <StatusMessage tone="info">Cargando reservas...</StatusMessage>
            ) : null}
            {reservationsQuery.error ? (
              <StatusMessage tone="error">
                {getErrorMessage(reservationsQuery.error)}
              </StatusMessage>
            ) : null}

            <div className="grid gap-3">
              {reservations.map((reservation) => (
                <button
                  key={reservation.id}
                  className={[
                    "w-full rounded-3xl border p-4 text-left transition",
                    selectedReservationId === reservation.id
                      ? "border-brand-400/60 bg-brand-500/10"
                      : "border-white/10 bg-white/5 hover:border-brand-400/30",
                  ].join(" ")}
                  type="button"
                  onClick={() => setSelectedReservationId(reservation.id)}
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
                        {` · ${reservation.partySize} pax · ${reservation.channel}`}
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
                  No hay reservas para la fecha seleccionada.
                </StatusMessage>
              ) : null}
            </div>
          </ConfigCard>
        </div>

        <ConfigCard
          title={selectedReservation ? "Detalle de reserva" : "Crear reserva manual"}
          subtitle="La operativa vive en backend; esta vista solo acelera el flujo del front desk."
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
              onCreated={(reservationId) => {
                void refreshReservations();
                setSelectedReservationId(reservationId);
              }}
            />
          )}
        </ConfigCard>
      </div>
    </OperationsShell>
  );
}
