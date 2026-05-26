import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";

export function RestaurantSelectorPage() {
  const navigate = useNavigate();
  const { status, session, setActiveRestaurantId } = useAuth();

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
    <div className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <section className="w-full max-w-4xl rounded-[2rem] border border-white/10 bg-slate-950/70 p-6 shadow-2xl shadow-black/40 sm:p-8">
        <p className="text-sm uppercase tracking-[0.3em] text-brand-300">
          Contexto Activo
        </p>
        <h1 className="mt-4 text-3xl font-semibold text-white sm:text-4xl">
          Elige el restaurante con el que quieres trabajar
        </h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-400 sm:text-base">
          Tu usuario tiene acceso a varios restaurantes. Selecciona uno para
          cargar el contexto operativo y activar las rutas protegidas.
        </p>

        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {session.restaurants.map((restaurant) => (
            <button
              key={restaurant.id}
              type="button"
              className="rounded-[1.75rem] border border-white/10 bg-white/5 p-5 text-left transition hover:border-brand-400/40 hover:bg-brand-500/10"
              onClick={() => {
                setActiveRestaurantId(restaurant.id);
                navigate("/", { replace: true });
              }}
            >
              <p className="text-xs uppercase tracking-[0.3em] text-brand-300">
                {restaurant.slug}
              </p>
              <h2 className="mt-3 text-2xl font-semibold text-white">
                {restaurant.name}
              </h2>
              <p className="mt-3 text-sm leading-6 text-slate-400">
                Roles: {restaurant.roles.join(", ")}
              </p>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

