import type {
  PlanningDiningRoomResponse,
  PlanningReservationSummaryResponse,
} from "@/features/planning/types";
import { StatusPill } from "@/features/frontdesk/components/StatusPill";
import { normalizeTimeForInput } from "@/features/frontdesk/utils/frontdeskUtils";

const LABEL_WIDTH = 208;
const BLOCK_WIDTH = 88;

function timeToMinutes(value: string | null) {
  if (!value) {
    return 0;
  }

  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function reservationTone(status: PlanningReservationSummaryResponse["status"]) {
  switch (status) {
    case "PENDING":
      return "border-amber-400/40 bg-amber-500/20 text-amber-50";
    case "CONFIRMED":
      return "border-sky-400/40 bg-sky-500/20 text-sky-50";
    case "SEATED":
      return "border-emerald-400/40 bg-emerald-500/20 text-emerald-50";
    case "COMPLETED":
      return "border-slate-400/40 bg-slate-500/20 text-slate-100";
    case "CANCELLED":
      return "border-rose-400/40 bg-rose-500/20 text-rose-50";
    case "NO_SHOW":
      return "border-fuchsia-400/40 bg-fuchsia-500/20 text-fuchsia-50";
    default:
      return "border-white/10 bg-white/10 text-white";
  }
}

function reservationLeft(startMinutes: number, firstBlockMinutes: number) {
  return ((startMinutes - firstBlockMinutes) / 30) * BLOCK_WIDTH;
}

function reservationWidth(
  startMinutes: number,
  endMinutes: number,
) {
  return Math.max(((endMinutes - startMinutes) / 30) * BLOCK_WIDTH, 72);
}

export function PlanningGrid({
  diningRooms,
  timeBlocks,
  onSelectReservation,
  selectedReservationId,
}: {
  diningRooms: PlanningDiningRoomResponse[];
  timeBlocks: string[];
  onSelectReservation: (reservationId: number) => void;
  selectedReservationId: number | null;
}) {
  const firstBlockMinutes = timeToMinutes(timeBlocks[0] ?? "00:00");
  const gridWidth = Math.max(timeBlocks.length * BLOCK_WIDTH, 720);

  return (
    <div className="grid gap-6">
      {diningRooms.map((room) => (
        <section
          key={room.id}
          className="rounded-lg border border-white/10 bg-slate-950/65 p-4 shadow-2xl shadow-black/20"
        >
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-xl font-semibold text-white">{room.name}</h3>
              <p className="mt-1 text-sm text-slate-400">
                Prioridad {room.priority} · {room.accessible ? "Accessible" : "Not accessible"} ·{" "}
                {room.tables.length} tables
              </p>
            </div>
          </div>

          <div className="overflow-x-auto">
            <div style={{ minWidth: LABEL_WIDTH + gridWidth }}>
              <div className="sticky top-0 z-10 flex border-b border-white/10 bg-slate-950/90 backdrop-blur">
                <div
                  className="shrink-0 px-4 py-3 text-xs font-semibold uppercase text-slate-500"
                  style={{ width: LABEL_WIDTH }}
                >
                  Table
                </div>
                <div className="relative flex-1" style={{ width: gridWidth }}>
                  <div className="flex">
                    {timeBlocks.map((timeBlock) => (
                      <div
                        key={`${room.id}-${timeBlock}`}
                        className="shrink-0 border-l border-white/10 px-3 py-3 text-xs font-semibold uppercase text-slate-500"
                        style={{ width: BLOCK_WIDTH }}
                      >
                        {timeBlock}
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="grid gap-2 pt-2">
                {room.tables.map((table) => (
                  <div key={table.id} className="flex items-stretch">
                    <div
                      className="shrink-0 rounded-lg border border-white/10 bg-white/5 px-4 py-4"
                      style={{ width: LABEL_WIDTH }}
                    >
                      <p className="text-sm font-semibold text-white">
                        {table.code}
                        {table.label ? ` · ${table.label}` : ""}
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        {table.minCapacity}-{table.maxCapacity} pax
                      </p>
                    </div>

                    <div
                      className="relative ml-2 overflow-hidden rounded-lg border border-white/10 bg-[linear-gradient(180deg,rgba(255,255,255,0.04),rgba(255,255,255,0.02))]"
                      style={{ width: gridWidth, minHeight: 96 }}
                    >
                      <div className="absolute inset-0 flex">
                        {timeBlocks.map((timeBlock) => (
                          <div
                            key={`${table.id}-${timeBlock}`}
                            className="h-full shrink-0 border-l border-white/10"
                            style={{ width: BLOCK_WIDTH }}
                          />
                        ))}
                      </div>

                      {table.reservations.map((reservation) => {
                        const startMinutes = timeToMinutes(reservation.startTime);
                        const endMinutes = timeToMinutes(
                          reservation.effectiveEndTime ?? reservation.endTime,
                        );
                        const left = reservationLeft(startMinutes, firstBlockMinutes);
                        const width = reservationWidth(startMinutes, endMinutes);

                        return (
                          <button
                            key={`${table.id}-${reservation.reservationId}`}
                            type="button"
                            className={[
                              "absolute top-3 flex h-[72px] flex-col justify-between rounded-lg border px-3 py-2 text-left shadow-lg transition",
                              reservationTone(reservation.status),
                              selectedReservationId === reservation.reservationId
                                ? "ring-2 ring-white/80"
                                : "hover:ring-2 hover:ring-brand-300/60",
                            ].join(" ")}
                            style={{ left, width }}
                            onClick={() => onSelectReservation(reservation.reservationId)}
                          >
                            <div className="flex items-start justify-between gap-2">
                              <p className="line-clamp-1 text-sm font-semibold">
                                {reservation.customerName || "Customer"}
                              </p>
                              <span className="text-xs font-semibold">
                                {reservation.partySize}p
                              </span>
                            </div>
                            <div className="flex items-end justify-between gap-2">
                              <p className="text-xs opacity-90">
                                {normalizeTimeForInput(reservation.startTime)}
                                {reservation.effectiveEndTime
                                  ? ` - ${normalizeTimeForInput(reservation.effectiveEndTime)}`
                                  : ""}
                              </p>
                              <div className="scale-90 origin-right">
                                <StatusPill status={reservation.status} />
                              </div>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>
      ))}
    </div>
  );
}
