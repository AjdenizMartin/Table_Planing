import { expect, test, type Page, type Route } from "@playwright/test";

const reservation = {
  reservationId: 99,
  customerId: 77,
  customerName: "Ada Rivera",
  status: "CONFIRMED",
  partySize: 8,
  reservationDate: "2026-07-15",
  startTime: "19:00:00",
  endTime: "21:00:00",
  effectiveEndTime: "21:15:00",
  estimatedDurationMin: 120,
  cleaningBufferMin: 15,
  accessibilityRequired: false,
  specialRequests: "Quiet table",
  assignmentType: null,
  tableId: null,
  tableCode: null,
  tableCombinationId: null,
  tableCombinationName: null,
  operationalCostLevel: null,
  setupTimeMinutes: 0,
  assignedResources: [],
};

const suggestions = [
  suggestion(31, "Table T12", "TABLE", false, 91, []),
  suggestion(42, "Extended terrace", "TABLE_COMBINATION", true, 73.5, [{
    storageResourceId: 7,
    resourceType: "EXTRA_CHAIR",
    resourceName: "Extra chairs",
    requiredQuantity: 2,
    availableQuantity: 4,
    capacityPerUnit: 1,
    capacityContribution: 2,
  }]),
  suggestion(43, "Private dining room", "TABLE_COMBINATION", true, 62, []),
];

test("manager logs in, compares top 3 and applies an advanced option", async ({ page }, testInfo) => {
  const selections: unknown[] = [];
  await mockApi(page, "MANAGER", selections);

  await loginAndOpenReservation(page);
  await page.getByRole("button", { name: "View suggestions" }).click();

  const panel = page.getByRole("region", { name: "Assignment suggestions" });
  await expect(panel.getByText("Extended terrace")).toBeVisible();
  await expect(panel.getByText("2 x Extra chairs")).toBeVisible();
  await expect(panel.getByRole("button", { name: "Apply" })).toHaveCount(3);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    await page.evaluate(() => window.innerWidth),
  );
  await captureVisualAudit(page, testInfo.outputPath("assignment-suggestions.png"));

  await panel.getByRole("article").filter({ hasText: "Extended terrace" }).getByRole("button", { name: "Apply" }).click();
  await expect(page.getByText("Assignment applied and resources reserved.")).toBeVisible();
  expect(selections).toEqual([{ candidateType: "TABLE_COMBINATION", candidateId: 42 }]);
});

test("staff can inspect the reservation but cannot approve suggestions", async ({ page }) => {
  await mockApi(page, "WAITER", []);
  await loginAndOpenReservation(page);

  await expect(page.getByRole("dialog", { name: "Reservation details" })).toBeVisible();
  await expect(page.getByRole("button", { name: "View suggestions" })).toHaveCount(0);
});

test("manager can delete a customer after explicit confirmation", async ({ page }, testInfo) => {
  const deletions: number[] = [];
  await mockApi(page, "MANAGER", [], deletions);

  await login(page);
  await openCustomer(page);
  await expect(page.getByRole("heading", { name: "Ada Rivera" })).toBeVisible();
  await page.getByRole("button", { name: "Delete customer" }).click();
  await expect(page.getByRole("alertdialog", { name: "Delete customer" })).toBeVisible();
  await captureVisualAudit(page, testInfo.outputPath("customer-delete-dialog.png"));
  await page.getByRole("button", { name: "Delete permanently" }).click();

  await expect(page).toHaveURL(/\/customers$/);
  expect(deletions).toEqual([77]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    await page.evaluate(() => window.innerWidth),
  );
});

test("staff can inspect a customer but cannot delete it", async ({ page }) => {
  await mockApi(page, "WAITER", []);

  await login(page);
  await openCustomer(page);
  await expect(page.getByRole("heading", { name: "Ada Rivera" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Delete customer" })).toHaveCount(0);
});

async function loginAndOpenReservation(page: Page) {
  await login(page);
  await page.getByRole("link", { name: "Planning", exact: true }).click();
  await page.getByRole("button", { name: /Ada Rivera/ }).first().click();
  await expect(page.getByRole("dialog", { name: "Reservation details" })).toContainText("Ada Rivera");
}

async function login(page: Page) {
  await page.goto("/login");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/planning$/);
}

async function openCustomer(page: Page) {
  await page.getByRole("link", { name: "Customers" }).click();
  await page.getByRole("link", { name: "View profile" }).click();
}

async function captureVisualAudit(page: Page, path: string) {
  if (process.env.VISUAL_AUDIT === "1") {
    await page.screenshot({ path });
  }
}

async function mockApi(
  page: Page,
  role: "MANAGER" | "WAITER",
  selections: unknown[],
  deletions: number[] = [],
) {
  await page.route("http://localhost:8080/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === "/api/auth/login") {
      return json(route, {
        accessToken: "test-access-token",
        refreshToken: "test-refresh-token",
        expiresIn: 900,
        user: { id: 5, name: "Pilot User", email: "pilot@example.com" },
        restaurants: [{ id: 1, name: "Pilot Restaurant", slug: "pilot", roles: [role] }],
      });
    }
    if (request.method() === "DELETE" && path.endsWith("/customers/77")) {
      deletions.push(77);
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith("/notifications/unread-count")) return json(route, { count: 0 });
    if (path.endsWith("/ai/insights/summary")) return json(route, { LOW: 0, MEDIUM: 0, HIGH: 0 });
    if (path.endsWith("/ai/insights")) return json(route, []);
    if (path.endsWith("/planning")) return json(route, planningFixture());
    if (path.endsWith("/assignment-suggestions")) {
      return json(route, { reservationId: 99, suggestions, rejectionReasons: [] });
    }
    if (path.endsWith("/assignment-selection")) {
      selections.push(request.postDataJSON());
      return json(route, {
        assigned: true,
        reservationId: 99,
        assignmentId: 501,
        assignmentType: "MANUAL",
        diningRoomId: 2,
        tableId: null,
        tableCode: null,
        tableCombinationId: 42,
        tableCombinationName: "Extended terrace",
        score: 73.5,
        summary: "Advanced manual selection",
        explanationJson: "{}",
        reasons: [],
        recommendedStartTime: null,
        recommendationSummary: null,
        operationalCostLevel: "MEDIUM",
        setupTimeMinutes: 20,
        resources: [],
      });
    }
    if (path.endsWith("/assignment-history")) return json(route, []);
    if (path.endsWith("/reservations/99")) return json(route, fullReservationFixture());
    if (path.endsWith("/customers/77")) return json(route, customerFixture());
    if (path.endsWith("/customers")) return json(route, [customerFixture()]);
    if (path.includes("/notifications")) return json(route, []);
    if (path === "/api/system/ping") return json(route, { status: "ok" });

    return json(route, []);
  });
}

function planningFixture() {
  return {
    date: "2026-07-15",
    restaurant: { id: 1, name: "Pilot Restaurant", timezone: "Europe/Madrid" },
    diningRooms: [{
      id: 2,
      name: "Main dining room",
      priority: 1,
      accessible: true,
      active: true,
      layoutWidth: 1000,
      layoutHeight: 700,
      tables: [{
        id: 31,
        code: "T12",
        label: null,
        minCapacity: 2,
        maxCapacity: 8,
        active: true,
        x: 100,
        y: 100,
        width: 140,
        height: 90,
        reservations: [],
      }],
    }],
    assignedReservations: [],
    unassignedReservations: [reservation],
    conflicts: [],
    timeBlocks: ["19:00", "19:30", "20:00"],
  };
}

function fullReservationFixture() {
  return {
    id: 99,
    restaurantId: 1,
    customerId: 77,
    customerFirstName: "Ada",
    customerLastName: "Rivera",
    channel: "PHONE",
    status: "CONFIRMED",
    partySize: 8,
    reservationDate: "2026-07-15",
    startTime: "19:00:00",
    endTime: "21:00:00",
    estimatedDurationMin: 120,
    cleaningBufferMin: 15,
    confirmedAt: "2026-07-15T10:00:00Z",
    cancelledAt: null,
    specialRequests: "Quiet table",
    accessibilityRequired: false,
    createdAt: "2026-07-10T10:00:00Z",
    updatedAt: "2026-07-10T10:00:00Z",
  };
}

function customerFixture() {
  return {
    id: 77,
    restaurantId: 1,
    firstName: "Ada",
    lastName: "Rivera",
    phone: "+34 600 000 000",
    email: "ada@example.com",
    notes: null,
    tagsJson: null,
    mobilityNeeds: null,
    createdAt: "2026-07-10T10:00:00Z",
    updatedAt: "2026-07-10T10:00:00Z",
  };
}

function suggestion(
  candidateId: number,
  displayName: string,
  candidateType: "TABLE" | "TABLE_COMBINATION",
  advanced: boolean,
  score: number,
  resources: Array<{
    storageResourceId: number;
    resourceType: string;
    resourceName: string;
    requiredQuantity: number;
    availableQuantity: number;
    capacityPerUnit: number;
    capacityContribution: number;
  }>,
) {
  return {
    candidateType,
    candidateId,
    displayName,
    tableIds: candidateType === "TABLE" ? [candidateId] : [10, 11],
    minCapacity: 6,
    maxCapacity: 10,
    score,
    advanced,
    operationalCostLevel: advanced ? "MEDIUM" : "LOW",
    setupTimeMinutes: advanced ? 20 : 0,
    resources,
    explanation: {
      summary: "Sufficient capacity and validated availability.",
      reasons: ["Available"],
      bonuses: {},
      penalties: {},
    },
  };
}

async function json(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
