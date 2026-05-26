export type AiInsightType =
  | "WASTED_LARGE_TABLE"
  | "DEAD_GAP_OPPORTUNITY"
  | "SUBOPTIMAL_ROOM_USAGE"
  | "CAPACITY_UNDERUTILIZED"
  | "HIGH_NO_SHOW_RISK"
  | "OVER_ASSIGNED_COMBINATION";

export type AiSeverity = "LOW" | "MEDIUM" | "HIGH";

export interface AiInsight {
  id: number;
  restaurantId: number;
  date: string;
  type: AiInsightType;
  severity: AiSeverity;
  title: string;
  description: string;
  entityType: string | null;
  entityId: number | null;
  metadataJson: string | null;
  dismissed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AiInsightSummary {
  LOW: number;
  MEDIUM: number;
  HIGH: number;
}
