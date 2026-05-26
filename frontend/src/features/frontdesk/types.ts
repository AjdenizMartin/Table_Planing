export interface CustomerResponse {
  id: number;
  restaurantId: number;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  email: string | null;
  notes: string | null;
  tagsJson: string | null;
  mobilityNeeds: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ReservationStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SEATED"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";

export type ReservationChannel =
  | "MANUAL"
  | "PHONE"
  | "WEB"
  | "GOOGLE"
  | "INSTAGRAM"
  | "FACEBOOK"
  | "WHATSAPP";

export interface ReservationResponse {
  id: number;
  restaurantId: number;
  customerId: number;
  customerFirstName: string | null;
  customerLastName: string | null;
  channel: ReservationChannel;
  status: ReservationStatus;
  partySize: number;
  reservationDate: string;
  startTime: string;
  endTime: string | null;
  estimatedDurationMin: number;
  cleaningBufferMin: number;
  confirmedAt: string | null;
  cancelledAt: string | null;
  specialRequests: string | null;
  accessibilityRequired: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerPayload {
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  email: string | null;
  notes: string | null;
  tagsJson: string | null;
  mobilityNeeds: string | null;
}

export interface UpdateCustomerPayload extends CreateCustomerPayload {}

export interface CreateReservationPayload {
  customerId: number;
  channel: ReservationChannel;
  partySize: number;
  reservationDate: string;
  startTime: string;
  endTime: string | null;
  estimatedDurationMin: number;
  cleaningBufferMin: number;
  specialRequests: string | null;
  accessibilityRequired: boolean;
}

export interface UpdateReservationPayload {
  customerId?: number;
  channel?: ReservationChannel;
  partySize?: number;
  reservationDate?: string;
  startTime?: string;
  endTime?: string | null;
  estimatedDurationMin?: number;
  cleaningBufferMin?: number;
  specialRequests?: string | null;
  accessibilityRequired?: boolean;
}
