import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/context/AuthContext";
import { RestaurantStompClient } from "@/features/realtime/stompClient";
import { notify } from "@/features/notifications/components/NotificationToast";
import type {
  RealtimeConnectionStatus,
  RealtimeEvent,
} from "@/features/realtime/types";

interface RealtimeContextValue {
  status: RealtimeConnectionStatus;
}

const RealtimeContext = createContext<RealtimeContextValue | undefined>(undefined);

function buildWebSocketUrl(apiBaseUrl: string, accessToken: string) {
  const url = new URL(apiBaseUrl || window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws";
  url.searchParams.set("access_token", accessToken);
  return url.toString();
}

export function RealtimeProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const { status: authStatus, session } = useAuth();
  const [status, setStatus] = useState<RealtimeConnectionStatus>("disconnected");

  useEffect(() => {
    if (
      authStatus !== "authenticated" ||
      !session.accessToken ||
      session.activeRestaurantId === null
    ) {
      setStatus("disconnected");
      return;
    }

    const restaurantId = session.activeRestaurantId;
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
    const client = new RestaurantStompClient({
      url: buildWebSocketUrl(apiBaseUrl, session.accessToken),
      topics: [
        `/topic/restaurants/${restaurantId}/planning`,
        `/topic/restaurants/${restaurantId}/reservations`,
        `/topic/restaurants/${restaurantId}/notifications`,
        `/topic/restaurants/${restaurantId}/ai`,
      ],
      onEvent: (event: RealtimeEvent) => {
        if (event.restaurantId !== restaurantId) {
          return;
        }

        if (event.type.startsWith("reservation.")) {
          void queryClient.invalidateQueries({ queryKey: ["reservations", restaurantId] });
          void queryClient.invalidateQueries({ queryKey: ["planning", restaurantId] });
          return;
        }

        if (event.type === "table.updated") {
          void queryClient.invalidateQueries({ queryKey: ["planning", restaurantId] });
          void queryClient.invalidateQueries({ queryKey: ["tables", restaurantId] });
          void queryClient.invalidateQueries({ queryKey: ["tableCombinations", restaurantId] });
          return;
        }

        if (event.type === "planning.recalculated") {
          void queryClient.invalidateQueries({ queryKey: ["planning", restaurantId] });
          void queryClient.invalidateQueries({ queryKey: ["aiInsights", restaurantId] });
          return;
        }

        if (event.type === "ai.insights.updated") {
          void queryClient.invalidateQueries({ queryKey: ["aiInsights", restaurantId] });
          return;
        }

        if (event.type === "notification") {
          void queryClient.invalidateQueries({ queryKey: ["notifications", restaurantId] });
          if (event.message) {
            notify(event.message, "");
          }
        }
      },
      onStatusChange: setStatus,
    });

    client.connect();
    return () => {
      client.disconnect();
    };
  }, [authStatus, queryClient, session.accessToken, session.activeRestaurantId]);

  const value = useMemo(() => ({ status }), [status]);

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime() {
  const context = useContext(RealtimeContext);
  if (!context) {
    throw new Error("useRealtime must be used within RealtimeProvider");
  }
  return context;
}
