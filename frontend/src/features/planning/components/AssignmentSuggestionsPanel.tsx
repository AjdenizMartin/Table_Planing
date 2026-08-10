import type { AssignmentSuggestionResponse } from "@/features/planning/types";
import { Check, RefreshCw } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

interface Props {
  suggestions: AssignmentSuggestionResponse[];
  loading: boolean;
  errorMessage: string | null;
  selecting: boolean;
  onRefresh: () => void;
  onSelect: (candidate: Pick<AssignmentSuggestionResponse, "candidateType" | "candidateId">) => void;
}

export function AssignmentSuggestionsPanel({
  suggestions,
  loading,
  errorMessage,
  selecting,
  onRefresh,
  onSelect,
}: Props) {
  const { t } = useI18n();
  return (
    <section className="border-y border-white/10 py-4" aria-label={t("Assignment suggestions")}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-white">{t("Available options")}</h3>
        </div>
        <button
          type="button"
          className="inline-flex h-10 items-center gap-2 rounded-lg border border-white/10 px-3 text-xs text-slate-300"
          onClick={onRefresh}
        >
          <RefreshCw className="h-3.5 w-3.5" />
          {t("Refresh")}
        </button>
      </div>

      {loading ? <p className="text-sm text-slate-400">{t("Calculating options...")}</p> : null}
      {errorMessage ? (
        <div role="alert" className="rounded-lg border border-rose-300/20 bg-rose-500/10 p-3 text-sm text-rose-100">
          {errorMessage}
        </div>
      ) : null}

      <div className="grid gap-3">
        {suggestions.slice(0, 3).map((suggestion, index) => (
          <article
            key={`${suggestion.candidateType}-${suggestion.candidateId}`}
            className="border-b border-white/10 pb-3 last:border-0"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-semibold text-brand-300">{t("Option")} {index + 1}</p>
                <h4 className="mt-1 truncate text-sm font-semibold text-white">{suggestion.displayName}</h4>
                <p className="mt-1 text-xs text-slate-400">
                  {suggestion.minCapacity}-{suggestion.maxCapacity} {t("guests")} · {t("Score")} {suggestion.score.toFixed(1)}
                </p>
              </div>
              <button
                type="button"
                className="inline-flex h-10 shrink-0 items-center gap-2 rounded-lg bg-brand-500 px-4 text-xs font-semibold text-slate-950 disabled:opacity-50"
                disabled={selecting}
                onClick={() => onSelect({
                  candidateType: suggestion.candidateType,
                  candidateId: suggestion.candidateId,
                })}
              >
                <Check className="h-3.5 w-3.5" />
                {t("Apply")}
              </button>
            </div>
            {suggestion.advanced ? (
              <p className="mt-2 text-xs text-amber-200">
                {t("Advanced")} · {t("cost")} {t(costLabel(suggestion.operationalCostLevel))} · {suggestion.setupTimeMinutes} min
              </p>
            ) : null}
            {suggestion.resources.length > 0 ? (
              <p className="mt-1 text-xs text-slate-300">
                {suggestion.resources
                  .map((resource) => `${resource.requiredQuantity} x ${resource.resourceName}`)
                  .join(", ")}
              </p>
            ) : null}
            <p className="mt-2 text-xs leading-5 text-slate-400">{suggestion.explanation.summary}</p>
          </article>
        ))}
      </div>

      {!loading && !errorMessage && suggestions.length === 0 ? (
        <p className="text-sm text-slate-400">{t("No options are currently available.")}</p>
      ) : null}
    </section>
  );
}

function costLabel(level: "LOW" | "MEDIUM" | "HIGH") {
  return level === "LOW" ? "low" : level === "MEDIUM" ? "medium" : "high";
}
