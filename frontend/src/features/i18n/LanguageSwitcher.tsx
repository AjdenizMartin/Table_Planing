import { Languages } from "lucide-react";
import { useI18n, type Language } from "@/features/i18n/I18nProvider";

const options: Array<{ value: Language; label: string }> = [
  { value: "es", label: "ES" },
  { value: "en", label: "EN" },
];

export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { language, setLanguage, t } = useI18n();

  return (
    <div
      className="inline-flex h-9 items-center rounded-lg border border-white/10 bg-white/5 p-1"
      aria-label={t("Cambiar idioma")}
      role="group"
    >
      {!compact ? (
        <Languages className="mx-1.5 h-4 w-4 text-slate-400" aria-hidden="true" />
      ) : null}
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={[
            "h-7 min-w-9 rounded-md px-2 text-xs font-semibold transition",
            language === option.value
              ? "bg-white text-slate-950 shadow-sm"
              : "text-slate-400 hover:text-white",
          ].join(" ")}
          aria-pressed={language === option.value}
          aria-label={t(option.value === "es" ? "Español" : "Ingles")}
          title={t(option.value === "es" ? "Español" : "Ingles")}
          onClick={() => setLanguage(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
