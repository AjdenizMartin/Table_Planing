import { apiClient } from "@/services/api/client";
import type {
  AssignReservationResponse,
  AssignmentHistoryItemResponse,
  AssignmentSuggestionsResponse,
  MoveReservationPayload,
  PlanningDayResponse,
} from "@/features/planning/types";

export function getPlanning(restaurantId: number, date: string) {
  return apiClient.request<PlanningDayResponse>(
    `/api/restaurants/${restaurantId}/planning?date=${encodeURIComponent(date)}`,
  );
}

export function getAssignmentSuggestions(restaurantId: number, reservationId: number) {
  return apiClient.request<AssignmentSuggestionsResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/assignment-suggestions`,
  );
}

export function selectAssignment(
  restaurantId: number,
  reservationId: number,
  payload: { candidateType: "TABLE" | "TABLE_COMBINATION"; candidateId: number },
) {
  return apiClient.request<AssignReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/assignment-selection`,
    { method: "POST", body: payload },
  );
}

export function getAssignmentHistory(restaurantId: number, reservationId: number) {
  return apiClient.request<AssignmentHistoryItemResponse[]>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/assignment-history`,
  );
}

export function recalculatePlanning(restaurantId: number, date: string) {
  return apiClient.request<PlanningDayResponse>(
    `/api/restaurants/${restaurantId}/planning/recalculate?date=${encodeURIComponent(date)}`,
    {
      method: "POST",
    },
  );
}

export function moveReservation(
  restaurantId: number,
  payload: MoveReservationPayload,
) {
  return apiClient.request<PlanningDayResponse>(
    `/api/restaurants/${restaurantId}/planning/move-reservation`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function assignReservationAutomatically(
  restaurantId: number,
  reservationId: number,
) {
  return apiClient.request<AssignReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/assign`,
    {
      method: "POST",
    },
  );
}
