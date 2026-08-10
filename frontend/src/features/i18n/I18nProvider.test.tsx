import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { I18nProvider, useI18n } from "./I18nProvider";

function TranslationProbe() {
  const { language, t } = useI18n();
  return <p>{language}: {t("Customers")}</p>;
}

describe("I18nProvider", () => {
  it("exposes English copy", () => {
    render(
      <I18nProvider>
        <TranslationProbe />
      </I18nProvider>,
    );

    expect(screen.getByText("en: Customers")).toBeInTheDocument();
  });
});
