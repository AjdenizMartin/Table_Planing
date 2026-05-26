import { apiClient } from "@/services/api/client";
import type {
  AuthResponse,
  LoginPayload,
  LogoutPayload,
  MeResponse,
  RefreshPayload,
} from "@/features/auth/types";

export function login(payload: LoginPayload) {
  return apiClient.request<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: payload,
    auth: false,
  });
}

export function refresh(payload: RefreshPayload) {
  return apiClient.request<AuthResponse>("/api/auth/refresh", {
    method: "POST",
    body: payload,
    auth: false,
  });
}

export function logout(payload: LogoutPayload) {
  return apiClient.request<void>("/api/auth/logout", {
    method: "POST",
    body: payload,
    auth: false,
  });
}

export function getCurrentUser(activeRestaurantId: number | null) {
  void activeRestaurantId;
  return apiClient.request<MeResponse>("/api/auth/me", {
    includeRestaurantHeader: true,
  });
}
