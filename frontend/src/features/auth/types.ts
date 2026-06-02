export interface AuthUser {
  id: number;
  name: string;
  email: string;
}

export interface RestaurantAccess {
  id: number;
  name: string;
  slug: string;
  roles: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
  restaurants: RestaurantAccess[];
}

export interface MeResponse {
  user: AuthUser;
  restaurants: RestaurantAccess[];
  activeRestaurantId: number | null;
}

export interface RegisterPayload {
  email: string;
  password: string;
  name: string;
  restaurantName: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RefreshPayload {
  refreshToken: string;
}

export interface LogoutPayload {
  refreshToken: string;
}

export interface AuthSession {
  accessToken: string | null;
  refreshToken: string | null;
  expiresIn: number | null;
  user: AuthUser | null;
  restaurants: RestaurantAccess[];
  activeRestaurantId: number | null;
}

export type AuthStatus = "loading" | "authenticated" | "anonymous";

