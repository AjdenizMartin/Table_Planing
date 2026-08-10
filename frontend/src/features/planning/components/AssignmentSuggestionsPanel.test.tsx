import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AssignmentSuggestionsPanel } from "./AssignmentSuggestionsPanel";
import type { AssignmentSuggestionResponse } from "@/features/planning/types";
import { I18nProvider } from "@/features/i18n/I18nProvider";
import type { ReactElement } from "react";

const advancedSuggestion: AssignmentSuggestionResponse = {
  candidateType: "TABLE_COMBINATION",
  candidateId: 42,
  displayName: "Extended terrace",
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
    resourceName: "Extra chairs",
    requiredQuantity: 2,
    availableQuantity: 4,
    capacityPerUnit: 1,
    capacityContribution: 2,
  }],
  explanation: {
    summary: "Fits the party while keeping the main dining room available.",
    reasons: ["Sufficient capacity"],
    bonuses: {},
    penalties: { operationalCost: 24, setupTime: 10 },
  },
};

function renderPanel(panel: ReactElement) {
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

    expect(screen.getByText("Extended terrace")).toBeInTheDocument();
    expect(screen.getByText(/cost medium · 20 min/i)).toBeInTheDocument();
    expect(screen.getByText("2 x Extra chairs")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Apply" }));
    expect(onSelect).toHaveBeenCalledWith({ candidateType: "TABLE_COMBINATION", candidateId: 42 });
  });

  it("renders conflict feedback without presenting a false empty state", () => {
    renderPanel(
      <AssignmentSuggestionsPanel
        suggestions={[]}
        loading={false}
        errorMessage="The required inventory is no longer available."
        selecting={false}
        onRefresh={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("The required inventory is no longer available.");
    expect(screen.queryByText("No viable options are available right now.")).not.toBeInTheDocument();
  });

  it("never renders more than three server candidates", () => {
    const suggestions = Array.from({ length: 4 }, (_, index) => ({
      ...advancedSuggestion,
      candidateId: index + 1,
      displayName: `Option ${index + 1}`,
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

    expect(screen.getAllByRole("button", { name: "Apply" })).toHaveLength(3);
    expect(screen.queryByText("Option 4", { selector: "h4" })).not.toBeInTheDocument();
  });
});
