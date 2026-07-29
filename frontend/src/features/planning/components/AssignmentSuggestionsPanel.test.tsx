import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AssignmentSuggestionsPanel } from "./AssignmentSuggestionsPanel";
import type { AssignmentSuggestionResponse } from "@/features/planning/types";
import { I18nProvider } from "@/features/i18n/I18nProvider";
import type { ReactElement } from "react";

const advancedSuggestion: AssignmentSuggestionResponse = {
  candidateType: "TABLE_COMBINATION",
  candidateId: 42,
  displayName: "Terraza ampliada",
  tableIds: [10, 11],
  minCapacity: 6,
  maxCapacity: 10,
  score: 73.5,
  advanced: true,
  operationalCostLevel: "MEDIUM",
  setupTimeMinutes: 20,
  resources: [{
    storageResourceId: 7,
    resourceType: "EXTRA_CHAIR",
    resourceName: "Sillas extra",
    requiredQuantity: 2,
    availableQuantity: 4,
    capacityPerUnit: 1,
    capacityContribution: 2,
  }],
  explanation: {
    summary: "Cabe el grupo y mantiene libre la sala principal.",
    reasons: ["Capacidad suficiente"],
    bonuses: {},
    penalties: { operationalCost: 24, setupTime: 10 },
  },
};

function renderPanel(panel: ReactElement) {
  window.localStorage.setItem("table-planning-language", "es");
  return render(
    <I18nProvider>
      {panel}
    </I18nProvider>,
  );
}

describe("AssignmentSuggestionsPanel", () => {
  it("compares an advanced option and applies the selected candidate", () => {
    const onSelect = vi.fn();
    renderPanel(
      <AssignmentSuggestionsPanel
        suggestions={[advancedSuggestion]}
        loading={false}
        errorMessage={null}
        selecting={false}
        onRefresh={vi.fn()}
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText("Terraza ampliada")).toBeInTheDocument();
    expect(screen.getByText(/coste medio · 20 min/i)).toBeInTheDocument();
    expect(screen.getByText("2 x Sillas extra")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Aplicar" }));
    expect(onSelect).toHaveBeenCalledWith({ candidateType: "TABLE_COMBINATION", candidateId: 42 });
  });

  it("renders conflict feedback without presenting a false empty state", () => {
    renderPanel(
      <AssignmentSuggestionsPanel
        suggestions={[]}
        loading={false}
        errorMessage="El inventario acaba de agotarse."
        selecting={false}
        onRefresh={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("El inventario acaba de agotarse.");
    expect(screen.queryByText("No hay opciones viables en este momento.")).not.toBeInTheDocument();
  });

  it("never renders more than three server candidates", () => {
    const suggestions = Array.from({ length: 4 }, (_, index) => ({
      ...advancedSuggestion,
      candidateId: index + 1,
      displayName: `Opcion ${index + 1}`,
    }));

    renderPanel(
      <AssignmentSuggestionsPanel
        suggestions={suggestions}
        loading={false}
        errorMessage={null}
        selecting={false}
        onRefresh={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getAllByRole("button", { name: "Aplicar" })).toHaveLength(3);
    expect(screen.queryByText("Opcion 4", { selector: "h4" })).not.toBeInTheDocument();
  });
});
