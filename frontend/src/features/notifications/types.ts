export type NotificationType =
  | "RESERVATION_CREATED"
  | "RESERVATION_CONFIRMED"
  | "RESERVATION_CANCELLED"
  | "RESERVATION_NO_SHOW"
  | "RESERVATION_SEATED"
  | "RESERVATION_COMPLETED"
  | "RESERVATION_UPDATED"
  | "RESERVATION_ASSIGNED"
  | "RESERVATION_REMINDER"
  | "SYSTEM";

export interface Notification {
  id: number;
  restaurantId: number;
  userId: number | null;
  type: NotificationType;
  title: string;
  body: string | null;
  entityType: string | null;
  entityId: number | null;
  read: boolean;
  createdAt: string;
}
