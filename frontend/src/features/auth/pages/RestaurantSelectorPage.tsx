import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { LanguageSwitcher } from "@/features/i18n/LanguageSwitcher";
import { useI18n } from "@/features/i18n/I18nProvider";

export function RestaurantSelectorPage() {
  const navigate = useNavigate();
  const { status, session, setActiveRestaurantId } = useAuth();
  const { t } = useI18n();

  if (status !== "authenticated") {
    return <Navigate to="/login" replace />;
  }

  if (session.restaurants.length === 0) {
    return <Navigate to="/login" replace />;
  }

  if (session.activeRestaurantId !== null) {
    return <Navigate to="/" replace />;
  }

  return (
    <main className="grid min-h-screen place-items-center bg-[#0c0f0e] px-4 py-8">
      <section className="w-full max-w-2xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-white">
            {t("Selecciona un restaurante")}
          </h1>
          <LanguageSwitcher compact />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          {session.restaurants.map((restaurant) => (
            <button
              key={restaurant.id}
              type="button"
              className="rounded-lg border border-white/10 bg-[#111614] p-5 text-left transition hover:border-emerald-400/50 hover:bg-[#151d19]"
              onClick={() => {
                setActiveRestaurantId(restaurant.id);
                navigate("/", { replace: true });
              }}
            >
              <h2 className="text-lg font-semibold text-white">
                {restaurant.name}
              </h2>
              <p className="mt-2 text-sm text-slate-400">
                {restaurant.roles.map((role) => t(role)).join(", ")}
              </p>
            </button>
          ))}
        </div>
      </section>
    </main>
  );
}
