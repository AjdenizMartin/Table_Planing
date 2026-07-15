import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "@/services/api/client";
import { useAuth } from "@/features/auth/context/AuthContext";
import { registrationEnabled } from "@/features/auth/registration";

interface LocationState {
  from?: {
    pathname?: string;
  };
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, status, session } = useAuth();
  const [email, setEmail] = useState(import.meta.env.DEV ? "demo@restaurant.com" : "");
  const [password, setPassword] = useState(import.meta.env.DEV ? "Demo1234!" : "");

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (nextSession) => {
      const nextPath =
        nextSession.restaurants.length > 1 && nextSession.activeRestaurantId === null
          ? "/select-restaurant"
          : ((location.state as LocationState | null)?.from?.pathname ?? "/");
      navigate(nextPath, { replace: true });
    },
  });

  if (status === "authenticated") {
    return (
      <Navigate
        to={
          session.restaurants.length > 1 && session.activeRestaurantId === null
            ? "/select-restaurant"
            : "/"
        }
        replace
      />
    );
  }

  const errorMessage = getLoginErrorMessage(mutation.error);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate({
      email,
      password,
    });
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <div className="grid w-full max-w-6xl gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="rounded-[2rem] border border-white/10 bg-white/5 p-8 shadow-2xl shadow-black/30 sm:p-10">
          <p className="text-sm uppercase tracking-[0.35em] text-brand-300">
            Restaurant Table Planning
          </p>
          <h1 className="mt-5 max-w-xl text-4xl font-semibold tracking-tight text-white sm:text-5xl">
            Opera reservas y planning diario con una sesion centralizada.
          </h1>
          <p className="mt-5 max-w-2xl text-base leading-7 text-slate-300 sm:text-lg">
            Esta base de autenticacion prepara el frontend para trabajar con varios
            restaurantes, mantener sesion y proteger rutas operativas desde tablet
            o escritorio.
          </p>

          <div className="mt-8 grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Sesion persistente</h2>
              <p className="mt-2 text-sm text-slate-400">
                Refresh token para recuperar sesion sin rehacer login a cada recarga.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Multi-restaurante</h2>
              <p className="mt-2 text-sm text-slate-400">
                Seleccion de restaurante activo cuando el usuario tiene varios accesos.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Ruta protegida</h2>
              <p className="mt-2 text-sm text-slate-400">
                El frontend limpia sesion al recibir `401` y vuelve al flujo de acceso.
              </p>
            </div>
          </div>
        </section>

        <section className="rounded-[2rem] border border-white/10 bg-slate-950/70 p-6 shadow-2xl shadow-black/40 sm:p-8">
          <div className="mx-auto max-w-md">
            <p className="text-sm uppercase tracking-[0.3em] text-brand-300">
              Iniciar Sesion
            </p>
            <h2 className="mt-4 text-3xl font-semibold text-white">
              Accede a tu operativa diaria
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-400">
              Usa las credenciales creadas en backend. El restaurante activo se
              resolvera despues del login si hace falta.
            </p>

            {import.meta.env.DEV ? (
              <div className="mt-5 rounded-2xl border border-emerald-400/25 bg-emerald-500/10 px-4 py-4 text-sm text-emerald-100">
                <p className="font-semibold">Acceso demo local</p>
                <p className="mt-2">
                  Email: <span className="font-mono">demo@restaurant.com</span>
                </p>
                <p className="mt-1">
                  Password: <span className="font-mono">Demo1234!</span>
                </p>
              </div>
            ) : null}

            <form className="mt-8 grid gap-5" onSubmit={handleSubmit}>
              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-200">Email</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
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
                <span className="text-sm font-medium text-slate-200">Password</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="********"
                  required
                />
              </label>

              {errorMessage ? (
                <div className="rounded-2xl border border-rose-400/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  {errorMessage}
                </div>
              ) : null}

              <button
                className="mt-2 h-14 rounded-2xl bg-brand-500 px-5 text-base font-semibold text-slate-950 transition hover:bg-brand-400 disabled:cursor-not-allowed disabled:opacity-60"
                type="submit"
                disabled={mutation.isPending}
              >
                {mutation.isPending ? "Entrando..." : "Entrar"}
              </button>
            </form>

            {registrationEnabled ? (
              <p className="mt-6 text-center text-sm text-slate-400">
                No tienes una cuenta?{" "}
                <Link to="/register" className="font-medium text-brand-400 hover:text-brand-300 transition">
                  Crea tu restaurante
                </Link>
              </p>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}

function getLoginErrorMessage(error: unknown) {
  if (!error) {
    return null;
  }

  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) {
      return "Credenciales incorrectas. Revisa el email y la contrasena.";
    }

    if (error.status >= 500) {
      return "El backend no esta disponible o ha devuelto un error. Revisa que este arrancado en http://localhost:8080.";
    }

    return error.message || "No se pudo iniciar sesion.";
  }

  if (error instanceof TypeError) {
    return "No se pudo conectar con el backend. Revisa Docker, el puerto 8080 y la configuracion CORS.";
  }

  return "Error inesperado al iniciar sesion.";
}
