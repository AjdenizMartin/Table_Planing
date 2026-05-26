import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";
import * as authApi from "@/features/auth/api/authApi";
import {
  clearStoredSession,
  loadStoredSession,
  persistSession,
} from "@/features/auth/storage";
import type {
  AuthResponse,
  AuthSession,
  AuthStatus,
  LoginPayload,
  RestaurantAccess,
} from "@/features/auth/types";
import { apiClient } from "@/services/api/client";

interface AuthContextValue {
  status: AuthStatus;
  session: AuthSession;
  login: (payload: LoginPayload) => Promise<AuthSession>;
  logout: () => Promise<void>;
  setActiveRestaurantId: (restaurantId: number) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function normalizeActiveRestaurantId(
  restaurants: RestaurantAccess[],
  desiredRestaurantId: number | null,
) {
  if (restaurants.length === 0) {
    return null;
  }

  if (desiredRestaurantId !== null && restaurants.some((restaurant) => restaurant.id === desiredRestaurantId)) {
    return desiredRestaurantId;
  }

  if (restaurants.length === 1) {
    return restaurants[0].id;
  }

  return null;
}

function mergeAuthResponseIntoSession(
  currentSession: AuthSession,
  authResponse: AuthResponse,
): AuthSession {
  return {
    accessToken: authResponse.accessToken,
    refreshToken: authResponse.refreshToken,
    expiresIn: authResponse.expiresIn,
    user: authResponse.user,
    restaurants: authResponse.restaurants,
    activeRestaurantId: normalizeActiveRestaurantId(
      authResponse.restaurants,
      currentSession.activeRestaurantId,
    ),
  };
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [session, setSession] = useState<AuthSession>(() => loadStoredSession());
  const sessionRef = useRef(session);

  useEffect(() => {
    sessionRef.current = session;
    if (session.user || session.refreshToken) {
      persistSession(session);
    }
  }, [session]);

  useEffect(() => {
    apiClient.configure({
      getSession: () => sessionRef.current,
      onSessionRefresh: (authResponse) => {
        setSession((currentSession) => mergeAuthResponseIntoSession(currentSession, authResponse));
        setStatus("authenticated");
      },
      onUnauthorized: () => {
        clearStoredSession();
        setSession({
          accessToken: null,
          refreshToken: null,
          expiresIn: null,
          user: null,
          restaurants: [],
          activeRestaurantId: null,
        });
        setStatus("anonymous");
      },
    });
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      const storedSession = loadStoredSession();

      if (!storedSession.refreshToken && !storedSession.accessToken) {
        if (!cancelled) {
          clearStoredSession();
          setSession(storedSession);
          setStatus("anonymous");
        }
        return;
      }

      try {
        let activeSession = storedSession;

        if (!storedSession.accessToken && storedSession.refreshToken) {
          const authResponse = await authApi.refresh({
            refreshToken: storedSession.refreshToken,
          });
          activeSession = mergeAuthResponseIntoSession(storedSession, authResponse);
        }

        const me = await authApi.getCurrentUser(activeSession.activeRestaurantId);
        const normalizedActiveRestaurantId = normalizeActiveRestaurantId(
          me.restaurants,
          activeSession.activeRestaurantId ?? me.activeRestaurantId ?? null,
        );

        if (!cancelled) {
          const nextSession: AuthSession = {
            accessToken: activeSession.accessToken,
            refreshToken: activeSession.refreshToken,
            expiresIn: activeSession.expiresIn,
            user: me.user,
            restaurants: me.restaurants,
            activeRestaurantId: normalizedActiveRestaurantId,
          };

          setSession(nextSession);
          setStatus("authenticated");
        }
      } catch {
        if (!cancelled) {
          clearStoredSession();
          setSession({
            accessToken: null,
            refreshToken: null,
            expiresIn: null,
            user: null,
            restaurants: [],
            activeRestaurantId: null,
          });
          setStatus("anonymous");
        }
      }
    }

    bootstrap();

    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      async login(payload) {
        const authResponse = await authApi.login(payload);
        const nextSession = mergeAuthResponseIntoSession(sessionRef.current, authResponse);
        persistSession(nextSession);
        setSession(nextSession);
        setStatus("authenticated");
        return nextSession;
      },
      async logout() {
        const refreshToken = sessionRef.current.refreshToken;

        try {
          if (refreshToken) {
            await authApi.logout({ refreshToken });
          }
        } finally {
          clearStoredSession();
          setSession({
            accessToken: null,
            refreshToken: null,
            expiresIn: null,
            user: null,
            restaurants: [],
            activeRestaurantId: null,
          });
          setStatus("anonymous");
        }
      },
      setActiveRestaurantId(restaurantId) {
        setSession((currentSession) => {
          const nextActiveRestaurantId = normalizeActiveRestaurantId(
            currentSession.restaurants,
            restaurantId,
          );

          if (nextActiveRestaurantId !== restaurantId) {
            return currentSession;
          }

          return {
            ...currentSession,
            activeRestaurantId: restaurantId,
          };
        });
      },
    }),
    [session, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
