import type { AuthResponse, AuthSession } from "@/features/auth/types";

export class ApiError extends Error {
  status: number;
  details?: unknown;

  constructor(message: string, status: number, details?: unknown) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

type HttpMethod = "GET" | "POST" | "PATCH" | "PUT" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  auth?: boolean;
  includeRestaurantHeader?: boolean;
  signal?: AbortSignal;
}

interface ClientConfig {
  getSession: () => AuthSession;
  onSessionRefresh: (authResponse: AuthResponse) => void;
  onUnauthorized: () => void;
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

class ApiClient {
  private config: ClientConfig | null = null;
  private refreshPromise: Promise<AuthResponse> | null = null;

  configure(config: ClientConfig) {
    this.config = config;
  }

  async request<T>(path: string, options: RequestOptions = {}, attempt = 0): Promise<T> {
    const config = this.config;
    const session = config?.getSession();
    const headers = new Headers();

    headers.set("Accept", "application/json");

    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    if (options.auth !== false && session?.accessToken) {
      headers.set("Authorization", `Bearer ${session.accessToken}`);
    }

    if (
      options.includeRestaurantHeader &&
      session?.activeRestaurantId !== null &&
      session?.activeRestaurantId !== undefined
    ) {
      headers.set("X-Restaurant-Id", String(session.activeRestaurantId));
    }

    const response = await fetch(`${apiBaseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });

    if (response.status === 401 && options.auth !== false && attempt === 0 && config) {
      const refreshed = await this.tryRefreshSession(config);
      if (refreshed) {
        return this.request<T>(path, options, attempt + 1);
      }
    }

    if (!response.ok) {
      throw await this.buildApiError(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  async refreshTokens(refreshToken: string): Promise<AuthResponse> {
    return this.request<AuthResponse>(
      "/api/auth/refresh",
      {
        method: "POST",
        body: { refreshToken },
        auth: false,
      },
      1,
    );
  }

  private async tryRefreshSession(config: ClientConfig): Promise<boolean> {
    const refreshToken = config.getSession().refreshToken;
    if (!refreshToken) {
      config.onUnauthorized();
      return false;
    }

    try {
      if (!this.refreshPromise) {
        this.refreshPromise = this.refreshTokens(refreshToken).finally(() => {
          this.refreshPromise = null;
        });
      }

      const authResponse = await this.refreshPromise;
      config.onSessionRefresh(authResponse);
      return true;
    } catch {
      config.onUnauthorized();
      return false;
    }
  }

  private async buildApiError(response: Response) {
    let message = `Request failed with status ${response.status}`;
    let details: unknown;

    try {
      details = await response.json();
      if (
        details &&
        typeof details === "object" &&
        "message" in details &&
        typeof (details as { message?: unknown }).message === "string"
      ) {
        message = (details as { message: string }).message;
      }
    } catch {
      details = undefined;
    }

    return new ApiError(message, response.status, details);
  }
}

export const apiClient = new ApiClient();

