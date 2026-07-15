import { describe, expect, it } from "vitest";
import { resourceRequirementsFromForm } from "./TableCombinationsPage";

describe("resourceRequirementsFromForm", () => {
  it("builds numeric inventory requirements for the backend contract", () => {
    expect(resourceRequirementsFromForm({ 7: "2", 11: "1" })).toEqual([
      { storageResourceId: 7, quantity: 2 },
      { storageResourceId: 11, quantity: 1 },
    ]);
  });

  it("keeps a standard combination resource-free", () => {
    expect(resourceRequirementsFromForm({})).toEqual([]);
  });
});
