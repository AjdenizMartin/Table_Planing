import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { I18nProvider, useI18n } from "./I18nProvider";
import { LanguageSwitcher } from "./LanguageSwitcher";

function TranslationProbe() {
  const { t } = useI18n();
  return <p>{t("Clientes")}</p>;
}

describe("I18nProvider", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("switches between Spanish and English and persists the choice", () => {
    window.localStorage.setItem("table-planning-language", "es");

    render(
      <I18nProvider>
        <LanguageSwitcher />
        <TranslationProbe />
      </I18nProvider>,
    );

    expect(screen.getByText("Clientes")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Ingles" }));

    expect(screen.getByText("Customers")).toBeInTheDocument();
    expect(window.localStorage.getItem("table-planning-language")).toBe("en");
    expect(document.documentElement.lang).toBe("en");
  });
});
