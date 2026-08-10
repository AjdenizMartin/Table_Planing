import type {
  CustomerResponse,
  ReservationResponse,
  ReservationStatus,
} from "@/features/frontdesk/types";

const statusLabels: Record<ReservationStatus, string> = {
  PENDING: "Pending",
  CONFIRMED: "Confirmed",
  ARRIVED: "Arrived",
  SEATED: "Seated",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  NO_SHOW: "No show",
};

export function formatCustomerName(customer: Pick<CustomerResponse, "firstName" | "lastName">) {
  const name = [customer.firstName, customer.lastName].filter(Boolean).join(" ").trim();
  return name || "Unnamed customer";
}

export function formatReservationCustomerName(
  reservation: Pick<ReservationResponse, "customerFirstName" | "customerLastName">,
) {
  const name = [reservation.customerFirstName, reservation.customerLastName]
    .filter(Boolean)
    .join(" ")
    .trim();
  return name || "Unnamed customer";
}

export function formatReservationStatus(status: ReservationStatus) {
  return statusLabels[status] ?? status;
}

export function normalizeTimeForInput(value: string | null) {
  if (!value) {
    return "";
  }

  return value.slice(0, 5);
}

export function normalizeTagsForInput(tagsJson: string | null) {
  if (!tagsJson) {
    return "";
  }

  try {
    const parsed = JSON.parse(tagsJson) as unknown;
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item)).join(", ");
    }
  } catch {
    return tagsJson;
  }

  return tagsJson;
}

export function tagsInputToJson(input: string) {
  const tags = input
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);

  if (tags.length === 0) {
    return null;
  }

  return JSON.stringify(tags);
}

export function todayDateValue() {
  return new Date().toISOString().slice(0, 10);
}
