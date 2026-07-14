import { apiClient } from "@/services/api/client";
import type {
  DiningRoomResponse,
  RestaurantResponse,
  RestaurantTableResponse,
  StorageResourceResponse,
  StorageResourceType,
  TableCombinationResponse,
} from "@/features/restaurant-config/types";

export function getRestaurant(restaurantId: number) {
  return apiClient.request<RestaurantResponse>(`/api/restaurants/${restaurantId}`);
}

export function updateRestaurant(
  restaurantId: number,
  payload: Partial<Pick<RestaurantResponse, "name" | "slug" | "timezone" | "phone" | "status">>,
) {
  return apiClient.request<RestaurantResponse>(`/api/restaurants/${restaurantId}`, {
    method: "PATCH",
    body: payload,
  });
}

export function getDiningRooms(restaurantId: number) {
  return apiClient.request<DiningRoomResponse[]>(
    `/api/restaurants/${restaurantId}/dining-rooms`,
  );
}

export function createDiningRoom(
  restaurantId: number,
  payload: {
    name: string;
    priority: number;
    accessible: boolean;
    active: boolean;
    layoutWidth: number;
    layoutHeight: number;
  },
) {
  return apiClient.request<DiningRoomResponse>(
    `/api/restaurants/${restaurantId}/dining-rooms`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function updateDiningRoom(
  restaurantId: number,
  diningRoomId: number,
  payload: Partial<{
    name: string;
    priority: number;
    accessible: boolean;
    active: boolean;
    layoutWidth: number;
    layoutHeight: number;
  }>,
) {
  return apiClient.request<DiningRoomResponse>(
    `/api/restaurants/${restaurantId}/dining-rooms/${diningRoomId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function deactivateDiningRoom(restaurantId: number, diningRoomId: number) {
  return apiClient.request<void>(
    `/api/restaurants/${restaurantId}/dining-rooms/${diningRoomId}`,
    {
      method: "DELETE",
    },
  );
}

export function getTables(restaurantId: number) {
  return apiClient.request<RestaurantTableResponse[]>(
    `/api/restaurants/${restaurantId}/tables`,
  );
}

export function createTable(
  restaurantId: number,
  payload: {
    diningRoomId: number | null;
    tableType: RestaurantTableResponse["tableType"];
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
  },
) {
  return apiClient.request<RestaurantTableResponse>(`/api/restaurants/${restaurantId}/tables`, {
    method: "POST",
    body: payload,
  });
}

export function updateTable(
  restaurantId: number,
  tableId: number,
  payload: Partial<{
    diningRoomId: number | null;
    tableType: RestaurantTableResponse["tableType"];
    code: string;
    label: string | null;
    minCapacity: number;
    maxCapacity: number;
    shape: string;
    active: boolean;
  }>,
) {
  return apiClient.request<RestaurantTableResponse>(
    `/api/restaurants/${restaurantId}/tables/${tableId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function updateTableLayout(
  restaurantId: number,
  tableId: number,
  payload: {
    x: number;
    y: number;
    width: number;
    height: number;
  },
) {
  return apiClient.request<RestaurantTableResponse>(
    `/api/restaurants/${restaurantId}/tables/${tableId}/layout`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function deactivateTable(restaurantId: number, tableId: number) {
  return apiClient.request<void>(`/api/restaurants/${restaurantId}/tables/${tableId}`, {
    method: "DELETE",
  });
}

export function getStorageResources(
  restaurantId: number,
  filters: { resourceType?: StorageResourceType; active?: boolean } = {},
) {
  const searchParams = new URLSearchParams();
  if (filters.resourceType) {
    searchParams.set("resourceType", filters.resourceType);
  }
  if (filters.active !== undefined) {
    searchParams.set("active", String(filters.active));
  }
  const query = searchParams.size > 0 ? `?${searchParams.toString()}` : "";

  return apiClient.request<StorageResourceResponse[]>(
    `/api/restaurants/${restaurantId}/storage-resources${query}`,
  );
}

export function createStorageResource(
  restaurantId: number,
  payload: {
    resourceType: StorageResourceResponse["resourceType"];
    name: string;
    quantity: number;
    capacityPerUnit: number;
    setupTimeMinutes: number;
    active: boolean;
    notes: string | null;
  },
) {
  return apiClient.request<StorageResourceResponse>(
    `/api/restaurants/${restaurantId}/storage-resources`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function updateStorageResource(
  restaurantId: number,
  resourceId: number,
  payload: Partial<{
    resourceType: StorageResourceResponse["resourceType"];
    name: string;
    quantity: number;
    capacityPerUnit: number;
    setupTimeMinutes: number;
    active: boolean;
    notes: string | null;
  }>,
) {
  return apiClient.request<StorageResourceResponse>(
    `/api/restaurants/${restaurantId}/storage-resources/${resourceId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function getTableCombinations(restaurantId: number) {
  return apiClient.request<TableCombinationResponse[]>(
    `/api/restaurants/${restaurantId}/table-combinations`,
  );
}

export function createTableCombination(
  restaurantId: number,
  payload: {
    name: string;
    minCapacity: number;
    maxCapacity: number;
    active: boolean;
    tableIds: number[];
  },
) {
  return apiClient.request<TableCombinationResponse>(
    `/api/restaurants/${restaurantId}/table-combinations`,
    {
      method: "POST",
      body: payload,
    },
  );
}

export function updateTableCombination(
  restaurantId: number,
  combinationId: number,
  payload: Partial<{
    name: string;
    minCapacity: number;
    maxCapacity: number;
    active: boolean;
    tableIds: number[];
  }>,
) {
  return apiClient.request<TableCombinationResponse>(
    `/api/restaurants/${restaurantId}/table-combinations/${combinationId}`,
    {
      method: "PATCH",
      body: payload,
    },
  );
}

export function deactivateTableCombination(
  restaurantId: number,
  combinationId: number,
) {
  return apiClient.request<void>(
    `/api/restaurants/${restaurantId}/table-combinations/${combinationId}`,
    {
      method: "DELETE",
    },
  );
}
