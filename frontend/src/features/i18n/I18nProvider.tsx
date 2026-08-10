import {
  createContext,
  useContext,
  useMemo,
  type PropsWithChildren,
} from "react";

type TranslationParams = Record<string, string | number>;

interface I18nContextValue {
  language: "en";
  t: (key: string, params?: TranslationParams) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: PropsWithChildren) {
  const value = useMemo<I18nContextValue>(
    () => ({
      language: "en",
      t: (key, params) => interpolate(key, params),
    }),
    [],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n must be used inside I18nProvider");
  }
  return context;
}

function interpolate(value: string, params?: TranslationParams) {
  if (!params) {
    return value;
  }
  return Object.entries(params).reduce(
    (result, [key, replacement]) =>
      result.split(`{{${key}}}`).join(String(replacement)),
    value,
  );
}
