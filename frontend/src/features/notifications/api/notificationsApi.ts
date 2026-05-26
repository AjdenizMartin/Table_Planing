import { apiClient } from "@/services/api/client";
import type { Notification } from "@/features/notifications/types";

export async function fetchNotifications(
  restaurantId: number,
  unreadOnly = false,
  limit = 50,
  signal?: AbortSignal,
): Promise<Notification[]> {
  const params = new URLSearchParams({
    unreadOnly: String(unreadOnly),
    limit: String(limit),
  });
  return apiClient.request<Notification[]>(
    `/api/restaurants/${restaurantId}/notifications?${params}`,
    { signal },
  );
}

export async function fetchUnreadCount(
  restaurantId: number,
  signal?: AbortSignal,
): Promise<number> {
  const data = await apiClient.request<{ count: number }>(
    `/api/restaurants/${restaurantId}/notifications/unread-count`,
    { signal },
  );
  return data.count;
}

export async function markAsRead(
  restaurantId: number,
  notificationId: number,
): Promise<void> {
  return apiClient.request(`/api/restaurants/${restaurantId}/notifications/${notificationId}/read`, {
    method: "PATCH",
  });
}

export async function markAllAsRead(restaurantId: number): Promise<number> {
  const data = await apiClient.request<{ marked: number }>(
    `/api/restaurants/${restaurantId}/notifications/read-all`,
    { method: "PATCH" },
  );
  return data.marked;
}
