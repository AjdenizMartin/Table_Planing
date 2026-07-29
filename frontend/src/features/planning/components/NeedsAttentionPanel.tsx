import { useMemo, useState } from "react";
import type { PlanningDayResponse, PlanningReservationSummaryResponse } from "@/features/planning/types";
import { normalizeTimeForInput } from "@/features/frontdesk/utils/frontdeskUtils";
import { useI18n } from "@/features/i18n/I18nProvider";

type Priority = "high" | "medium" | "low";

interface AttentionItem {
  id: string;
  priority: Priority;
  time: string;
  customerName: string;
  partySize: number;
  problem: string;
  action: string;
  reservationId: number | null;
}

const PRIORITY_ORDER: Record<Priority, number> = { high: 0, medium: 1, low: 2 };

const PRIORITY_STYLES: Record<Priority, { dot: string; border: string; bg: string }> = {
  high: {
    dot: "bg-rose-400",
    border: "border-l-rose-400/60",
    bg: "bg-rose-500/[0.04]",
  },
  medium: {
    dot: "bg-amber-400",
    border: "border-l-amber-400/60",
    bg: "bg-amber-400/[0.04]",
  },
  low: {
    dot: "bg-sky-400",
    border: "border-l-sky-400/60",
    bg: "bg-sky-400/[0.04]",
  },
};

function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function computeItems(planning: PlanningDayResponse): AttentionItem[] {
  const items: AttentionItem[] = [];
  const allReservations = [...planning.assignedReservations, ...planning.unassignedReservations];
  const now = new Date();
  const currentMinutes = now.getHours() * 60 + now.getMinutes();

  const seen = new Set<string>();

  function add(
    reservation: PlanningReservationSummaryResponse,
    problem: string,
    action: string,
    priority: Priority,
  ) {
    const key = `${reservation.reservationId}-${problem}`;
    if (seen.has(key)) return;
    seen.add(key);
    items.push({
      id: key,
      priority,
      time: normalizeTimeForInput(reservation.startTime),
      customerName: reservation.customerName ?? `#${reservation.reservationId}`,
      partySize: reservation.partySize,
      problem,
      action,
      reservationId: reservation.reservationId,
    });
  }

  // 1. Unconfirmed
  for (const r of allReservations) {
    if (r.status === "PENDING") {
      add(r, "Not confirmed", "Verify or confirm", "medium");
    }
  }

  // 2. Unassigned
  for (const r of allReservations) {
    if (r.tableId === null && r.tableCombinationId === null && r.status !== "CANCELLED" && r.status !== "NO_SHOW") {
      add(r, "No table assigned", "Assign a table", "high");
    }
  }

  // 3. Arriving soon (within 60 min)
  for (const r of allReservations) {
    if (r.status !== "CANCELLED" && r.status !== "NO_SHOW" && r.status !== "COMPLETED") {
      const startMin = timeToMinutes(r.startTime);
      const diff = startMin - currentMinutes;
      if (diff >= 0 && diff <= 60) {
        add(r, "Arriving soon", "Prepare table", "medium");
      }
    }
  }

  // 4. Large parties pending (6+ guests, unconfirmed or unassigned)
  for (const r of allReservations) {
    if (r.partySize >= 6) {
      if (r.status === "PENDING") {
        add(r, "Large party pending", "Plan table assignment", "medium");
      } else if (r.tableId === null && r.tableCombinationId === null && r.status !== "CANCELLED" && r.status !== "NO_SHOW") {
        add(r, "Large party unassigned", "Find suitable table", "high");
      }
    }
  }

  // 5. Accessibility required
  for (const r of allReservations) {
    if (r.accessibilityRequired && r.status !== "CANCELLED" && r.status !== "NO_SHOW" && r.status !== "COMPLETED") {
      add(r, "Accessibility required", "Ensure accessible table", "medium");
    }
  }

  // 6. Conflicts
  for (const conflict of planning.conflicts) {
    for (const rid of conflict.reservationIds) {
      const reservation = allReservations.find((r) => r.reservationId === rid);
      if (reservation) {
        add(reservation, conflict.message, "Review conflict", "high");
      }
    }
  }

  // 7. Table handover (table still occupied when next arrives)
  for (const room of planning.diningRooms) {
    for (const table of room.tables) {
      if (!table.active) continue;
      const sorted = [...table.reservations].sort(
        (a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime),
      );
      for (let i = 0; i < sorted.length - 1; i++) {
        const current = sorted[i];
        const next = sorted[i + 1];
        if (
          current.status !== "CANCELLED" &&
          current.status !== "NO_SHOW" &&
          next.status !== "CANCELLED" &&
          next.status !== "NO_SHOW"
        ) {
          const currentEnd = timeToMinutes(current.effectiveEndTime ?? current.endTime ?? current.startTime);
          const nextStart = timeToMinutes(next.startTime);
          if (currentEnd > nextStart) {
            add(
              next,
              `Table ${table.code} still occupied`,
              "Check table readiness",
              "high",
            );
          }
        }
      }
    }
  }

  // 8. Pending reminder (PENDING or CONFIRMED)
  for (const r of allReservations) {
    if (r.status === "PENDING" || r.status === "CONFIRMED") {
      add(r, "Pending reminder", "Send confirmation or reminder", "low");
    }
  }

  items.sort((a, b) => {
    const pa = PRIORITY_ORDER[a.priority];
    const pb = PRIORITY_ORDER[b.priority];
    if (pa !== pb) return pa - pb;
    return timeToMinutes(a.time) - timeToMinutes(b.time);
  });

  return items;
}

interface Props {
  planning: PlanningDayResponse | undefined;
  onSelectReservation: (reservationId: number) => void;
}

export function NeedsAttentionPanel({ planning, onSelectReservation }: Props) {
  const { t } = useI18n();
  const [collapsed, setCollapsed] = useState(false);

  const items = useMemo(() => {
    if (!planning) return [];
    return computeItems(planning);
  }, [planning]);

  const highCount = items.filter((i) => i.priority === "high").length;
  const totalCount = items.length;

  const priorityLabel = (p: Priority) => {
    const style = PRIORITY_STYLES[p];
    return <span className={["h-2 w-2 rounded-full", style.dot].join(" ")} />;
  };

  return (
    <section className="overflow-hidden rounded-lg border border-white/[0.06] bg-slate-950/40">
      <button
        type="button"
        className="flex w-full items-center justify-between px-4 py-2.5 text-left transition hover:bg-white/[0.02] active:scale-[0.99]"
        onClick={() => setCollapsed((c) => !c)}
      >
        <div className="flex items-center gap-2.5">
          <span className="text-sm font-medium text-white">{t("Requiere atencion")}</span>
          <span className="rounded-md border border-white/10 bg-white/5 px-2 py-0.5 text-xs font-semibold text-slate-300">{totalCount}</span>
          {highCount > 0 ? (
            <span className="rounded-md border border-rose-400/30 bg-rose-500/15 px-2 py-0.5 text-xs font-semibold text-rose-200">{highCount} {t("urgentes")}</span>
          ) : null}
        </div>
        <span className="text-xs text-slate-500">{collapsed ? t("Mostrar") : t("Ocultar")}</span>
      </button>

      {!collapsed ? (
        <div className="max-h-[300px] overflow-y-auto border-t border-white/[0.04] px-4 py-2.5">
          {items.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-500">{t("No hay incidencias pendientes.")}</p>
          ) : (
            <div className="grid gap-1.5">
              {items.map((item) => {
                const style = PRIORITY_STYLES[item.priority];
                return (
                  <button
                    key={item.id}
                    type="button"
                    className={[
                      "flex items-center gap-3 rounded-lg border border-white/[0.04] border-l-2 px-3.5 py-2.5 text-left transition hover:bg-white/[0.03] active:scale-[0.98]",
                      style.border,
                      style.bg,
                    ].join(" ")}
                    onClick={() => { if (item.reservationId) onSelectReservation(item.reservationId); }}
                  >
                    {priorityLabel(item.priority)}
                    <span className="text-xs font-semibold text-white tabular-nums">{item.time}</span>
                    <span className="text-xs text-slate-400">·</span>
                    <span className="min-w-0 truncate text-sm font-medium text-white">{item.customerName}</span>
                    <span className="shrink-0 text-xs text-slate-400">{item.partySize}</span>
                    <span className="hidden min-w-0 truncate text-xs text-slate-500 sm:inline">· {t(item.problem)}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}
