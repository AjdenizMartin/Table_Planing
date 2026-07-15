export type RestaurantStatus = "ACTIVE" | "INACTIVE";

export interface RestaurantResponse {
  id: number;
  name: string;
  slug: string;
  timezone: string;
  phone: string | null;
  status: RestaurantStatus;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface DiningRoomResponse {
  id: number;
  restaurantId: number;
  name: string;
  priority: number;
  accessible: boolean;
  active: boolean;
  layoutWidth: number;
  layoutHeight: number;
  createdAt: string;
  updatedAt: string;
}

export interface RestaurantTableResponse {
  id: number;
  restaurantId: number;
  diningRoomId: number | null;
  tableType: "FIXED" | "MOVABLE" | "STORAGE" | "TEMPORARY";
  code: string;
  label: string | null;
  minCapacity: number;
  maxCapacity: number;
  shape: string;
  x: number;
  y: number;
  width: number;
  height: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type StorageResourceType =
  | "EXTRA_TABLE"
  | "EXTRA_CHAIR"
  | "HIGH_CHAIR"
  | "FOLDING_TABLE"
  | "TABLE_EXTENSION"
  | "BENCH"
  | "STORAGE_TABLE"
  | "OTHER";

export interface StorageResourceResponse {
  id: number;
  restaurantId: number;
  resourceType: StorageResourceType;
  name: string;
  quantity: number;
  capacityPerUnit: number;
  setupTimeMinutes: number;
  active: boolean;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TableCombinationItemResponse {
  id: number;
  tableId: number;
  diningRoomId: number | null;
  tableCode: string;
  tableLabel: string | null;
  orderIndex: number;
}

export type CombinationType = "STANDARD" | "ADVANCED";
export type OperationalCostLevel = "LOW" | "MEDIUM" | "HIGH";

export interface TableCombinationResourceRequirementResponse {
  id: number;
  storageResourceId: number;
  resourceType: StorageResourceType;
  resourceName: string;
  quantity: number;
  capacityPerUnit: number;
  capacityContribution: number;
  resourceSetupTimeMinutes: number;
}

export interface TableCombinationResponse {
  id: number;
  restaurantId: number;
  name: string;
  minCapacity: number;
  maxCapacity: number;
  active: boolean;
  combinationType: CombinationType;
  operationalCostLevel: OperationalCostLevel;
  setupTimeMinutes: number;
  items: TableCombinationItemResponse[];
  resourceRequirements: TableCombinationResourceRequirementResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiFieldDetail {
  field: string;
  message: string;
}

export interface ApiErrorPayload {
  code?: string;
  message?: string;
  details?: ApiFieldDetail[] | Record<string, unknown>;
  timestamp?: string;
}
