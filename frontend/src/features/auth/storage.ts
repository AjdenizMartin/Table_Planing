import type { AuthSession } from "@/features/auth/types";

const ACCESS_TOKEN_KEY = "rtp.access-token";
const SESSION_KEY = "rtp.auth-session";

const EMPTY_SESSION: AuthSession = {
  accessToken: null,
  refreshToken: null,
  expiresIn: null,
  user: null,
  restaurants: [],
  activeRestaurantId: null,
};

function canUseStorage() {
  return typeof window !== "undefined";
}

export function loadStoredSession(): AuthSession {
  if (!canUseStorage()) {
    return EMPTY_SESSION;
  }

  const rawSession = window.localStorage.getItem(SESSION_KEY);
  const accessToken = window.sessionStorage.getItem(ACCESS_TOKEN_KEY);

  if (!rawSession) {
    return {
      ...EMPTY_SESSION,
      accessToken,
    };
  }

  try {
    const parsed = JSON.parse(rawSession) as Omit<AuthSession, "accessToken">;
    return {
      accessToken,
      refreshToken: parsed.refreshToken ?? null,
      expiresIn: parsed.expiresIn ?? null,
      user: parsed.user ?? null,
      restaurants: parsed.restaurants ?? [],
      activeRestaurantId: parsed.activeRestaurantId ?? null,
    };
  } catch {
    clearStoredSession();
    return EMPTY_SESSION;
  }
}

export function persistSession(session: AuthSession) {
  if (!canUseStorage()) {
    return;
  }

  if (session.accessToken) {
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
  } else {
    window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  }

  window.localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      refreshToken: session.refreshToken,
      expiresIn: session.expiresIn,
      user: session.user,
      restaurants: session.restaurants,
      activeRestaurantId: session.activeRestaurantId,
    }),
  );
}

export function clearStoredSession() {
  if (!canUseStorage()) {
    return;
  }

  window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(SESSION_KEY);
}

