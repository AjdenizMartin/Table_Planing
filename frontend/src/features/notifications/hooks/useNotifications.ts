import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/context/AuthContext";
import {
  fetchNotifications,
  fetchUnreadCount,
  markAsRead,
  markAllAsRead,
} from "@/features/notifications/api/notificationsApi";

export function useNotifications(unreadOnly = false) {
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useQuery({
    queryKey: ["notifications", restaurantId, { unreadOnly }],
    queryFn: ({ signal }) =>
      fetchNotifications(restaurantId!, unreadOnly, 50, signal),
    enabled: restaurantId !== null,
    refetchInterval: 30_000,
  });
}

export function useUnreadCount() {
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useQuery({
    queryKey: ["notifications", restaurantId, "unreadCount"],
    queryFn: ({ signal }) => fetchUnreadCount(restaurantId!, signal),
    enabled: restaurantId !== null,
    refetchInterval: 15_000,
  });
}

export function useMarkAsRead() {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useMutation({
    mutationFn: (notificationId: number) => markAsRead(restaurantId!, notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications", restaurantId] });
      queryClient.invalidateQueries({ queryKey: ["notifications", restaurantId, "unreadCount"] });
    },
  });
}

export function useMarkAllAsRead() {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useMutation({
    mutationFn: () => markAllAsRead(restaurantId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications", restaurantId] });
      queryClient.invalidateQueries({ queryKey: ["notifications", restaurantId, "unreadCount"] });
    },
  });
}
