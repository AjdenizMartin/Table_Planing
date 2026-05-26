import { apiClient } from "@/services/api/client";
import type { AiInsight, AiInsightSummary } from "@/features/ai/types";

export function fetchAiInsights(restaurantId: number, date: string, signal?: AbortSignal) {
  return apiClient.request<AiInsight[]>(
    `/api/restaurants/${restaurantId}/ai/insights?date=${encodeURIComponent(date)}`,
    { signal },
  );
}

export function fetchAiInsightSummary(
  restaurantId: number,
  date: string,
  signal?: AbortSignal,
) {
  return apiClient.request<AiInsightSummary>(
    `/api/restaurants/${restaurantId}/ai/insights/summary?date=${encodeURIComponent(date)}`,
    { signal },
  );
}

export function dismissAiInsight(restaurantId: number, insightId: number) {
  return apiClient.request<AiInsight>(
    `/api/restaurants/${restaurantId}/ai/insights/${insightId}/dismiss`,
    {
      method: "PATCH",
    },
  );
}
