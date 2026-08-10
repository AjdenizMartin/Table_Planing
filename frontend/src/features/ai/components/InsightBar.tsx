import type { AiInsight, AiInsightSummary, AiSeverity } from "@/features/ai/types";
import { useI18n } from "@/features/i18n/I18nProvider";

function severityBadgeTone(severity: AiSeverity) {
  switch (severity) {
    case "HIGH":
      return "border-rose-400/30 bg-rose-500/10 text-rose-100";
    case "MEDIUM":
      return "border-amber-400/30 bg-amber-500/10 text-amber-100";
    case "LOW":
      return "border-sky-400/30 bg-sky-500/10 text-sky-100";
    default:
      return "border-white/10 bg-white/5 text-white";
  }
}

export function InsightBar({
  summary,
  insights,
  onOpenPanel,
}: {
  summary: AiInsightSummary | undefined;
  insights: AiInsight[];
  onOpenPanel: () => void;
}) {
  const { t } = useI18n();
  const activeCount = insights.filter((insight) => !insight.dismissed).length;
  const topInsights = insights.filter((insight) => !insight.dismissed).slice(0, 3);

  return (
    <section className="rounded-lg border border-white/10 bg-[#111614] p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-xs uppercase text-brand-300">{t("Suggestions")}</p>
          <h2 className="mt-2 text-2xl font-semibold text-white">
            {activeCount} {t("active recommendations")}
          </h2>
          <p className="mt-2 text-sm text-slate-400">
            {t("High")} {summary?.HIGH ?? 0} · {t("Medium")} {summary?.MEDIUM ?? 0} · {t("Low")} {summary?.LOW ?? 0}
          </p>
        </div>

        <button
          type="button"
          className="h-12 rounded-lg bg-brand-500 px-5 text-sm font-semibold text-slate-950 transition hover:bg-brand-400"
          onClick={onOpenPanel}
        >
          {t("View recommendations")}
        </button>
      </div>

      <div className="mt-5 grid gap-3 lg:grid-cols-3">
        {topInsights.length > 0 ? (
          topInsights.map((insight) => (
            <article
              key={insight.id}
              className="rounded-lg border border-white/10 bg-white/5 p-4"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h3 className="text-sm font-semibold text-white">{insight.title}</h3>
                <span
                  className={[
                    "rounded-full border px-3 py-1 text-[10px] font-semibold uppercase",
                    severityBadgeTone(insight.severity),
                  ].join(" ")}
                >
                  {insight.severity}
                </span>
              </div>
              <p className="mt-3 line-clamp-3 text-sm leading-6 text-slate-300">
                {insight.description}
              </p>
            </article>
          ))
        ) : (
          <div className="lg:col-span-3 rounded-lg border border-emerald-400/25 bg-emerald-500/10 p-4 text-sm text-emerald-100">
            {t("There are no active recommendations for this date.")}
          </div>
        )}
      </div>
    </section>
  );
}
