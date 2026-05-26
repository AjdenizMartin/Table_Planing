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

export function getReservations(restaurantId: number, date?: string) {
  const search = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiClient.request<ReservationResponse[]>(
    `/api/restaurants/${restaurantId}/reservations${search}`,
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
