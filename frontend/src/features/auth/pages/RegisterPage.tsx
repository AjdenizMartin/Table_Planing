import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { ApiError } from "@/services/api/client";
import { useAuth } from "@/features/auth/context/AuthContext";
import { LanguageSwitcher } from "@/features/i18n/LanguageSwitcher";
import { useI18n } from "@/features/i18n/I18nProvider";

export function RegisterPage() {
  const navigate = useNavigate();
  const { register, status, session } = useAuth();
  const { t } = useI18n();
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

  const errorMessage = getRegisterErrorMessage(mutation.error, t);
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
    <main className="grid min-h-screen place-items-center bg-[#0c0f0e] px-4 py-8">
      <section className="w-full max-w-md">
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
          <LanguageSwitcher compact />
        </div>

        <div className="rounded-lg border border-white/10 bg-[#111614] p-6 shadow-2xl shadow-black/25 sm:p-7">
          <h1 className="text-2xl font-semibold text-white">
            {t("Crear cuenta")}
          </h1>

            <form className="mt-7 grid gap-4" onSubmit={handleSubmit}>
              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-300">{t("Tu nombre")}</span>
                <input
                  className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                  type="text"
                  autoComplete="name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Ana Martinez"
                  required
                />
              </label>

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
                <span className="text-sm font-medium text-slate-300">{t("Nombre del restaurante")}</span>
                <input
                  className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                  type="text"
                  value={restaurantName}
                  onChange={(event) => setRestaurantName(event.target.value)}
                  placeholder="Mi Restaurante"
                  required
                />
              </label>

              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-300">{t("Contraseña")}</span>
                <input
                  className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder={t("Minimo 8 caracteres")}
                  minLength={8}
                  required
                />
              </label>

              <label className="grid gap-2">
                <span className="text-sm font-medium text-slate-300">{t("Confirmar contraseña")}</span>
                <input
                  className="h-11 rounded-lg border border-white/10 bg-[#0c100e] px-3 text-sm text-white outline-none transition focus:border-emerald-400/70 focus:ring-2 focus:ring-emerald-400/20"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder={t("Repite la contraseña")}
                  required
                />
              </label>

              {passwordMismatch ? (
                <div className="rounded-lg border border-rose-400/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  {t("Las contraseñas no coinciden.")}
                </div>
              ) : null}

              {errorMessage ? (
                <div className="rounded-lg border border-rose-400/25 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  {errorMessage}
                </div>
              ) : null}

              <button
                className="mt-2 h-11 rounded-lg bg-emerald-400 px-4 text-sm font-semibold text-[#0b110e] transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
                type="submit"
                disabled={mutation.isPending || passwordMismatch}
              >
                {mutation.isPending ? t("Creando cuenta...") : t("Crear cuenta")}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-slate-400">
              {t("Ya tienes una cuenta?")}{" "}
              <Link to="/login" className="font-medium text-emerald-300 transition hover:text-emerald-200">
                {t("Iniciar sesion")}
              </Link>
            </p>
        </div>
      </section>
    </main>
  );
}

function getRegisterErrorMessage(error: unknown, t: (key: string) => string) {
  if (!error) {
    return null;
  }

  if (error instanceof ApiError) {
    if (error.status === 409) {
      return t("Ese email ya esta registrado.");
    }

    if (error.status >= 500) {
      return t("El servicio no esta disponible. Intentalo de nuevo en unos minutos.");
    }

    return error.message || t("No se pudo completar el registro.");
  }

  if (error instanceof TypeError) {
    return t("No se pudo conectar con el servicio.");
  }

  return t("Error inesperado al registrarse.");
}
