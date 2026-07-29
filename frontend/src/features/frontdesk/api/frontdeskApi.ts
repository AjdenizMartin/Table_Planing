import { apiClient } from "@/services/api/client";
import type {
  CreateCustomerPayload,
  CreateReservationPayload,
  CustomerResponse,
  ReservationResponse,
  UpdateCustomerPayload,
  UpdateReservationPayload,
} from "@/features/frontdesk/types";

export function getCustomers(restaurantId: number, query?: string) {
  const search = query?.trim()
    ? `?query=${encodeURIComponent(query.trim())}`
    : "";
  return apiClient.request<CustomerResponse[]>(
    `/api/restaurants/${restaurantId}/customers${search}`,
  );
}

export function getCustomer(restaurantId: number, customerId: number) {
  return apiClient.request<CustomerResponse>(
    `/api/restaurants/${restaurantId}/customers/${customerId}`,
  );
}

export function createCustomer(restaurantId: number, payload: CreateCustomerPayload) {
  return apiClient.request<CustomerResponse>(
    `/api/restaurants/${restaurantId}/customers`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function updateCustomer(
  restaurantId: number,
  customerId: number,
  payload: UpdateCustomerPayload,
) {
  return apiClient.request<CustomerResponse>(
    `/api/restaurants/${restaurantId}/customers/${customerId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function deleteCustomer(restaurantId: number, customerId: number) {
  return apiClient.request<void>(
    `/api/restaurants/${restaurantId}/customers/${customerId}`,
    { method: "DELETE" },
  );
}

export function getReservations(restaurantId: number, date?: string) {
  const search = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiClient.request<ReservationResponse[]>(
    `/api/restaurants/${restaurantId}/reservations${search}`,
  );
}

export interface SearchReservationsParams {
  customerQuery?: string;
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  partySize?: number;
}

export function searchReservations(
  restaurantId: number,
  params: SearchReservationsParams,
) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      query.set(key, String(value));
    }
  });
  const qs = query.toString();
  return apiClient.request<ReservationResponse[]>(
    `/api/restaurants/${restaurantId}/reservations/search${qs ? `?${qs}` : ""}`,
  );
}

export function getReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}`,
  );
}

export function createReservation(
  restaurantId: number,
  payload: CreateReservationPayload,
) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function updateReservation(
  restaurantId: number,
  reservationId: number,
  payload: UpdateReservationPayload,
) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function arrivedReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/arrived`,
    {
      method: "POST",
    },
  );
}

export function confirmReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/confirm`,
    {
      method: "POST",
    },
  );
}

export function cancelReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/cancel`,
    {
      method: "POST",
    },
  );
}

export function seatReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/seat`,
    {
      method: "POST",
    },
  );
}

export function completeReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/complete`,
    {
      method: "POST",
    },
  );
}

export function noShowReservation(restaurantId: number, reservationId: number) {
  return apiClient.request<ReservationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/no-show`,
    {
      method: "POST",
    },
  );
}

export interface SendConfirmationResponse {
  id: number;
  status: string;
  errorMessage: string | null;
  sentAt: string | null;
}

export function sendReservationConfirmation(restaurantId: number, reservationId: number) {
  return apiClient.request<SendConfirmationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/notifications/confirmation`,
    {
      method: "POST",
    },
  );
}

export function sendReservationReminder(restaurantId: number, reservationId: number) {
  return apiClient.request<SendConfirmationResponse>(
    `/api/restaurants/${restaurantId}/reservations/${reservationId}/notifications/reminder`,
    {
      method: "POST",
    },
  );
}
