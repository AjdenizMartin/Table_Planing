import { describe, expect, it } from "vitest";
import { isRegistrationEnabled } from "./registration";

describe("isRegistrationEnabled", () => {
  it("keeps registration available when the setting is omitted", () => {
    expect(isRegistrationEnabled(undefined, false)).toBe(true);
  });

  it("allows registration to be disabled in development", () => {
    expect(isRegistrationEnabled("false", false)).toBe(false);
  });

  it("always disables registration in production builds", () => {
    expect(isRegistrationEnabled("true", true)).toBe(false);
  });
});
