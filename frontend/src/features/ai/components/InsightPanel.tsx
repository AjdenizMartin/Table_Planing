import type { AiInsight, AiSeverity } from "@/features/ai/types";
import { StatusMessage } from "@/features/restaurant-config/components/StatusMessage";
import { X } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

function severityTone(severity: AiSeverity) {
  switch (severity) {
    case "HIGH":
      return "border-rose-400/25 bg-rose-500/10 text-rose-100";
    case "MEDIUM":
      return "border-amber-400/25 bg-amber-500/10 text-amber-100";
    case "LOW":
      return "border-sky-400/25 bg-sky-500/10 text-sky-100";
    default:
      return "border-white/10 bg-white/5 text-white";
  }
}

export function InsightPanel({
  insights,
  open,
  onClose,
  onDismiss,
  dismissingInsightId,
  canDismiss,
}: {
  insights: AiInsight[];
  open: boolean;
  onClose: () => void;
  onDismiss: (insightId: number) => void;
  dismissingInsightId: number | null;
  canDismiss: boolean;
}) {
  const { t } = useI18n();
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-950/55 backdrop-blur-sm">
      <aside className="h-full w-full max-w-2xl overflow-y-auto border-l border-white/10 bg-slate-950 p-6 shadow-2xl shadow-black/50">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase text-brand-300">{t("Sugerencias")}</p>
            <h2 className="mt-2 text-2xl font-semibold text-white">{t("Recomendaciones del dia")}</h2>
          </div>
          <button
            type="button"
            className="grid h-11 w-11 place-items-center rounded-lg border border-white/10 bg-white/5 text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
            onClick={onClose}
            aria-label={t("Cerrar")}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-6 grid gap-4">
          {insights.filter((insight) => !insight.dismissed).length === 0 ? (
            <StatusMessage tone="info">
              {t("No hay recomendaciones activas para esta fecha.")}
            </StatusMessage>
          ) : null}

          {insights
            .filter((insight) => !insight.dismissed)
            .map((insight) => (
              <article
                key={insight.id}
                className="rounded-lg border border-white/10 bg-white/5 p-5"
              >
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-3">
                      <h3 className="text-lg font-semibold text-white">{insight.title}</h3>
                      <span
                        className={[
                          "rounded-full border px-3 py-1 text-[10px] font-semibold uppercase",
                          severityTone(insight.severity),
                        ].join(" ")}
                      >
                        {insight.severity}
                      </span>
                    </div>
                    <p className="mt-3 text-sm leading-6 text-slate-300">
                      {insight.description}
                    </p>
                    <p className="mt-3 text-xs uppercase text-slate-500">
                      {insight.type}
                      {insight.entityType ? ` · ${insight.entityType}` : ""}
                      {insight.entityId ? ` #${insight.entityId}` : ""}
                    </p>
                  </div>

                  <button
                    type="button"
                    className="h-11 rounded-lg border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-rose-400/40 hover:bg-rose-500/10 disabled:opacity-60"
                    disabled={!canDismiss || dismissingInsightId === insight.id}
                    onClick={() => onDismiss(insight.id)}
                  >
                    {!canDismiss
                      ? t("Solo manager u owner")
                      : dismissingInsightId === insight.id
                        ? t("Descartando...")
                        : t("Descartar")}
                  </button>
                </div>

              </article>
            ))}
        </div>
      </aside>
    </div>
  );
}
