import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/context/AuthContext";
import {
  dismissAiInsight,
  fetchAiInsights,
  fetchAiInsightSummary,
} from "@/features/ai/api/aiApi";

export function useAiInsights(date: string) {
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useQuery({
    queryKey: ["aiInsights", restaurantId, date],
    queryFn: ({ signal }) => fetchAiInsights(restaurantId!, date, signal),
    enabled: restaurantId !== null,
  });
}

export function useAiInsightsSummary(date: string) {
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;

  return useQuery({
    queryKey: ["aiInsights", restaurantId, date, "summary"],
    queryFn: ({ signal }) => fetchAiInsightSummary(restaurantId!, date, signal),
    enabled: restaurantId !== null,
  });
}

export function useDismissAiInsight(date: string) {
  const { session } = useAuth();
  const restaurantId = session.activeRestaurantId;
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (insightId: number) => dismissAiInsight(restaurantId!, insightId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aiInsights", restaurantId, date] });
      queryClient.invalidateQueries({
        queryKey: ["aiInsights", restaurantId, date, "summary"],
      });
    },
  });
}
