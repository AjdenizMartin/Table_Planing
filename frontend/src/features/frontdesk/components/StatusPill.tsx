import type { ReservationStatus } from "@/features/frontdesk/types";
import { formatReservationStatus } from "@/features/frontdesk/utils/frontdeskUtils";

export function StatusPill({ status }: { status: ReservationStatus }) {
  const toneByStatus: Record<ReservationStatus, string> = {
    PENDING: "border-amber-400/25 bg-amber-500/10 text-amber-100",
    CONFIRMED: "border-sky-400/25 bg-sky-500/10 text-sky-100",
    SEATED: "border-emerald-400/25 bg-emerald-500/10 text-emerald-100",
    COMPLETED: "border-slate-400/25 bg-slate-500/10 text-slate-200",
    CANCELLED: "border-rose-400/25 bg-rose-500/10 text-rose-100",
    NO_SHOW: "border-fuchsia-400/25 bg-fuchsia-500/10 text-fuchsia-100",
  };

  return (
    <span
      className={[
        "inline-flex rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em]",
        toneByStatus[status],
      ].join(" ")}
    >
      {formatReservationStatus(status)}
    </span>
  );
}
