import { useMutation } from "@tanstack/react-query";
import { ArrowRight, LockKeyhole } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "@/services/api/client";
import { useAuth } from "@/features/auth/context/AuthContext";
import { registrationEnabled } from "@/features/auth/registration";
import { useI18n } from "@/features/i18n/I18nProvider";

interface LocationState {
  from?: {
    pathname?: string;
  };
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, status, session } = useAuth();
  const { t } = useI18n();
  const [email, setEmail] = useState(import.meta.env.DEV ? "demo@restaurant.com" : "");
  const [password, setPassword] = useState(import.meta.env.DEV ? "Demo1234!" : "");

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (nextSession) => {
      const nextPath =
        nextSession.restaurants.length > 1 && nextSession.activeRestaurantId === null
          ? "/select-restaurant"
          : ((location.state as LocationState | null)?.from?.pathname ?? "/planning");
      navigate(nextPath, { replace: true });
    },
  });

  if (status === "authenticated") {
    return (
      <Navigate
        to={
          session.restaurants.length > 1 && session.activeRestaurantId === null
            ? "/select-restaurant"
            : "/planning"
        }
        replace
      />
    );
  }

  const errorMessage = getLoginErrorMessage(mutation.error, t);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate({ email, password });
  }

  return (
    <main className="grid min-h-screen place-items-center bg-[#0c0f0e] px-4 py-8">
      <section className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-lg bg-emerald-400 text-sm font-black text-[#0b110e]">
              TP
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Table Planning</p>
              <p className="text-xs text-slate-500">Restaurant OS</p>
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-white/10 bg-[#111614] p-6 shadow-2xl shadow-black/25 sm:p-7">
          <LockKeyhole className="mb-5 h-6 w-6 text-emerald-300" />
          <h1 className="text-2xl font-semibold text-white">
            {t("Sign in to Table Planning")}
          </h1>

          <form className="mt-7 grid gap-4" onSubmit={handleSubmit}>
            <label className="grid gap-2">
              <span className="text-sm font-medium text-slate-300">{t("Email")}</span>
              <input
                className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                type="email"
                inputMode="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="manager@restaurant.com"
                required
              />
            </label>

            <label className="grid gap-2">
              <span className="text-sm font-medium text-slate-300">
                {t("Password")}
              </span>
              <input
                className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="••••••••••••"
                required
              />
            </label>

            {errorMessage ? (
              <div
                className="rounded-lg border border-rose-400/25 bg-rose-500/10 px-3 py-3 text-sm text-rose-200"
                role="alert"
              >
                {errorMessage}
              </div>
            ) : null}

            <button
              className="mt-2 flex h-11 items-center justify-center gap-2 rounded-lg bg-emerald-400 px-4 text-sm font-semibold text-[#0b110e] transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
              type="submit"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? t("Signing in...") : t("Sign in")}
              {!mutation.isPending ? <ArrowRight className="h-4 w-4" /> : null}
            </button>
          </form>

          {registrationEnabled ? (
            <Link
              to="/register"
              className="mt-5 block text-center text-sm font-medium text-emerald-300 hover:text-emerald-200"
            >
              {t("Create account")}
            </Link>
          ) : null}
        </div>
      </section>
    </main>
  );
}

function getLoginErrorMessage(
  error: unknown,
  t: (key: string) => string,
) {
  if (!error) {
    return null;
  }

  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) {
      return t("Incorrect credentials. Check your email and password.");
    }
    if (error.status >= 500) {
      return t("The service is unavailable. Try again in a few minutes.");
    }
    return error.message || t("Could not sign in.");
  }

  if (error instanceof TypeError) {
    return t("Could not connect to the service.");
  }

  return t("Unexpected sign-in error.");
}
