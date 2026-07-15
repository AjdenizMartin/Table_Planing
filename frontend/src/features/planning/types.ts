import type { ReservationStatus } from "@/features/frontdesk/types";

export interface PlanningRestaurantSummaryResponse {
  id: number;
  name: string;
  timezone: string;
}

export interface PlanningReservationSummaryResponse {
  reservationId: number;
  customerId: number;
  customerName: string | null;
  status: ReservationStatus;
  partySize: number;
  reservationDate: string;
  startTime: string;
  endTime: string | null;
  effectiveEndTime: string | null;
  estimatedDurationMin: number;
  cleaningBufferMin: number;
  accessibilityRequired: boolean;
  specialRequests: string | null;
  assignmentType: string | null;
  tableId: number | null;
  tableCode: string | null;
  tableCombinationId: number | null;
  tableCombinationName: string | null;
  operationalCostLevel: "LOW" | "MEDIUM" | "HIGH" | null;
  setupTimeMinutes: number;
  assignedResources: PlanningAssignedResourceResponse[];
}

export interface PlanningAssignedResourceResponse {
  storageResourceId: number;
  resourceType: string;
  resourceName: string;
  quantity: number;
  capacityPerUnit: number;
}

export interface PlanningTableResponse {
  id: number;
  code: string;
  label: string | null;
  minCapacity: number;
  maxCapacity: number;
  active: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
  reservations: PlanningReservationSummaryResponse[];
}

export interface PlanningDiningRoomResponse {
  id: number;
  name: string;
  priority: number;
  accessible: boolean;
  active: boolean;
  layoutWidth: number;
  layoutHeight: number;
  tables: PlanningTableResponse[];
}

export interface PlanningConflictResponse {
  type: string;
  resourceType: string;
  resourceId: number;
  resourceLabel: string;
  reservationIds: number[];
  overlappingStart: string;
  overlappingEnd: string;
  message: string;
}

export interface PlanningDayResponse {
  date: string;
  restaurant: PlanningRestaurantSummaryResponse;
  diningRooms: PlanningDiningRoomResponse[];
  assignedReservations: PlanningReservationSummaryResponse[];
  unassignedReservations: PlanningReservationSummaryResponse[];
  conflicts: PlanningConflictResponse[];
  timeBlocks: string[];
}

export interface MoveReservationPayload {
  reservationId: number;
  tableId: number | null;
  tableCombinationId: number | null;
}

export interface AssignReservationResponse {
  assigned: boolean;
  reservationId: number;
  assignmentId: number | null;
  assignmentType: string | null;
  diningRoomId: number | null;
  tableId: number | null;
  tableCode: string | null;
  tableCombinationId: number | null;
  tableCombinationName: string | null;
  score: number | null;
  summary: string | null;
  explanationJson: string | null;
  reasons: string[];
  recommendedStartTime: string | null;
  recommendationSummary: string | null;
  operationalCostLevel: "LOW" | "MEDIUM" | "HIGH" | null;
  setupTimeMinutes: number | null;
  resources: AssignedResourceResponse[];
}

export interface AssignedResourceResponse {
  storageResourceId: number;
  resourceType: string;
  resourceName: string;
  quantity: number;
  capacityPerUnit: number;
  setupTimeMinutes: number;
}

export interface AssignmentSuggestionResponse {
  candidateType: "TABLE" | "TABLE_COMBINATION";
  candidateId: number;
  displayName: string;
  tableIds: number[];
  minCapacity: number;
  maxCapacity: number;
  score: number;
  advanced: boolean;
  operationalCostLevel: "LOW" | "MEDIUM" | "HIGH";
  setupTimeMinutes: number;
  resources: Array<{
    storageResourceId: number;
    resourceType: string;
    resourceName: string;
    requiredQuantity: number;
    availableQuantity: number;
    capacityPerUnit: number;
    capacityContribution: number;
  }>;
  explanation: {
    summary: string;
    reasons: string[];
    bonuses: Record<string, number>;
    penalties: Record<string, number>;
  };
}

export interface AssignmentSuggestionsResponse {
  reservationId: number;
  suggestions: AssignmentSuggestionResponse[];
  rejectionReasons: string[];
}

export interface AssignmentHistoryItemResponse {
  assignmentId: number;
  active: boolean;
  assignmentType: string;
  tableId: number | null;
  tableCode: string | null;
  tableCombinationId: number | null;
  tableCombinationName: string | null;
  score: number | null;
  operationalCostLevel: "LOW" | "MEDIUM" | "HIGH";
  setupTimeMinutes: number;
  assignedByUserId: number | null;
  assignedByName: string | null;
  assignedAt: string | null;
  explanationJson: string | null;
  resources: AssignedResourceResponse[];
}

export interface PlanningMoveOption {
  type: "table" | "combination";
  id: number;
  label: string;
  diningRoomId: number | null;
}
