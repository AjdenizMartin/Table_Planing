import { useNavigate } from "react-router-dom";
import { useAiInsightsSummary } from "@/features/ai/hooks/useAiInsights";
import { todayDateValue } from "@/features/frontdesk/utils/frontdeskUtils";

export function AiBadge() {
  const navigate = useNavigate();
  const date = todayDateValue();
  const { data } = useAiInsightsSummary(date);
  const total = (data?.LOW ?? 0) + (data?.MEDIUM ?? 0) + (data?.HIGH ?? 0);

  return (
    <button
      type="button"
      className="relative h-11 rounded-2xl border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
      onClick={() => navigate("/planning")}
      aria-label="AI insights"
    >
      <span className="text-xs uppercase tracking-[0.2em]">AI</span>
      {total > 0 ? (
        <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-amber-500 px-1 text-[10px] font-bold text-slate-950">
          {total > 99 ? "99+" : total}
        </span>
      ) : null}
    </button>
  );
}
