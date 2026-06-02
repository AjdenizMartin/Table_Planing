import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { ApiError } from "@/services/api/client";
import { useAuth } from "@/features/auth/context/AuthContext";

export function RegisterPage() {
  const navigate = useNavigate();
  const { register, status, session } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [restaurantName, setRestaurantName] = useState("");

  const mutation = useMutation({
    mutationFn: register,
    onSuccess: (nextSession) => {
      const nextPath =
        nextSession.restaurants.length > 0
          ? "/"
          : "/select-restaurant";
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

  const errorMessage = getRegisterErrorMessage(mutation.error);
  const passwordMismatch = confirmPassword.length > 0 && password !== confirmPassword;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (passwordMismatch) {
      return;
    }
    mutation.mutate({
      email,
      password,
      name,
      restaurantName,
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
            Crea tu restaurante y empieza a operar.
          </h1>
          <p className="mt-5 max-w-2xl text-base leading-7 text-slate-300 sm:text-lg">
            Registra tu restaurante en segundos. Al crear tu cuenta,
            se configurara automaticamente tu primer restaurante con
            acceso de propietario.
          </p>

          <div className="mt-8 grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Sin compromiso</h2>
              <p className="mt-2 text-sm text-slate-400">
                Empieza a usar la plataforma sin necesidad de configuracion inicial compleja.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Todo incluido</h2>
              <p className="mt-2 text-sm text-slate-400">
                Salones, mesas, combinaciones, reservas y planning listos para usar.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
              <h2 className="text-sm font-semibold text-white">Multi-dispositivo</h2>
              <p className="mt-2 text-sm text-slate-400">
                Opera desde tablet, movil o escritorio con la misma experiencia.
              </p>
            </div>
          </div>
        </section>

        <section className="rounded-[2rem] border border-white/10 bg-slate-950/70 p-6 shadow-2xl shadow-black/40 sm:p-8">
          <div className="mx-auto max-w-md">
            <p className="text-sm uppercase tracking-[0.3em] text-brand-300">
              Crear Cuenta
            </p>
            <h2 className="mt-4 text-3xl font-semibold text-white">
              Registra tu restaurante
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-400">
              Todos los campos son obligatorios.
            </p>

            <form className="mt-8 grid gap-5" onSubmit={handleSubmit}>
              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-200">Tu nombre</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                  type="text"
                  autoComplete="name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Ana Martinez"
                  required
                />
              </label>

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
                <span className="text-sm font-medium text-slate-200">Nombre del restaurante</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                  type="text"
                  value={restaurantName}
                  onChange={(event) => setRestaurantName(event.target.value)}
                  placeholder="Mi Restaurante"
                  required
                />
              </label>

              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-200">Contrasena</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Minimo 8 caracteres"
                  minLength={8}
                  required
                />
              </label>

              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-200">Confirmar contrasena</span>
                <input
                  className="h-14 rounded-2xl border border-white/10 bg-slate-900/80 px-4 text-base text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="Repite la contrasena"
                  required
                />
              </label>

              {passwordMismatch ? (
                <div className="rounded-2xl border border-rose-400/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  Las contrasenas no coinciden.
                </div>
              ) : null}

              {errorMessage ? (
                <div className="rounded-2xl border border-rose-400/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  {errorMessage}
                </div>
              ) : null}

              <button
                className="mt-2 h-14 rounded-2xl bg-brand-500 px-5 text-base font-semibold text-slate-950 transition hover:bg-brand-400 disabled:cursor-not-allowed disabled:opacity-60"
                type="submit"
                disabled={mutation.isPending || passwordMismatch}
              >
                {mutation.isPending ? "Creando cuenta..." : "Crear cuenta y entrar"}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-slate-400">
              Ya tienes una cuenta?{" "}
              <Link to="/login" className="font-medium text-brand-400 hover:text-brand-300 transition">
                Inicia sesion
              </Link>
            </p>
          </div>
        </section>
      </div>
    </div>
  );
}

function getRegisterErrorMessage(error: unknown) {
  if (!error) {
    return null;
  }

  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "Ese email ya esta registrado. Prueba con otro o inicia sesion.";
    }

    if (error.status >= 500) {
      return "El backend no esta disponible. Revisa que este arrancado en http://localhost:8080.";
    }

    return error.message || "No se pudo completar el registro.";
  }

  if (error instanceof TypeError) {
    return "No se pudo conectar con el backend. Revisa Docker, el puerto 8080 y la configuracion CORS.";
  }

  return "Error inesperado al registrarse.";
}
