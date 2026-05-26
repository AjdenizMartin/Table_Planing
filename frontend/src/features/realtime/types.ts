export type RealtimeConnectionStatus =
  | "disconnected"
  | "connecting"
  | "connected"
  | "error";

export interface RealtimeEvent {
  type: string;
  restaurantId: number;
  reservationId: number | null;
  tableId: number | null;
  diningRoomId: number | null;
  date: string | null;
  message: string | null;
  occurredAt: string;
}
