import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { AiBadge } from "@/features/ai/components/AiBadge";
import { useRealtime } from "@/features/realtime/RealtimeProvider";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { NotificationToast } from "@/features/notifications/components/NotificationToast";

export function AppLayout() {
  const { logout, session, setActiveRestaurantId } = useAuth();
  const { status: realtimeStatus } = useRealtime();
  const activeRestaurant =
    session.restaurants.find(
      (restaurant) => restaurant.id === session.activeRestaurantId,
    ) ?? null;

  const navigationItems = [
    { to: "/", label: "Inicio" },
    { to: "/planning", label: "Planning" },
    { to: "/customers", label: "Clientes" },
    { to: "/reservations", label: "Reservas" },
    { to: "/settings/restaurant", label: "Restaurante" },
    { to: "/settings/dining-rooms", label: "Salones" },
    { to: "/settings/tables", label: "Mesas" },
    { to: "/settings/layout", label: "Plano" },
    { to: "/settings/table-combinations", label: "Combinaciones" },
  ];

  return (
    <div className="min-h-screen">
      <header className="border-b border-white/10 bg-slate-950/60 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.3em] text-brand-300">
              Restaurant Table Planning
            </p>
            <h1 className="text-lg font-semibold text-white">
              Base operativa lista para autenticacion y contexto de restaurante
            </h1>
          </div>

          <div className="flex flex-col gap-3 lg:items-end">
            <div className="flex flex-wrap items-center gap-3">
              {session.restaurants.length > 1 ? (
                <label className="flex items-center gap-2">
                  <span className="text-xs uppercase tracking-[0.25em] text-slate-400">
                    Restaurante
                  </span>
                  <select
                    className="h-11 rounded-2xl border border-white/10 bg-slate-900/90 px-4 text-sm text-white outline-none transition focus:border-brand-400/70 focus:ring-2 focus:ring-brand-400/30"
                    value={session.activeRestaurantId ?? ""}
                    onChange={(event) => setActiveRestaurantId(Number(event.target.value))}
                  >
                    <option value="" disabled>
                      Seleccionar
                    </option>
                    {session.restaurants.map((restaurant) => (
                      <option key={restaurant.id} value={restaurant.id}>
                        {restaurant.name}
                      </option>
                    ))}
                  </select>
                </label>
              ) : activeRestaurant ? (
                <span className="rounded-full border border-brand-400/30 bg-brand-500/10 px-4 py-2 text-xs font-medium text-brand-200">
                  {activeRestaurant.name}
                </span>
              ) : null}

              <AiBadge />
              <NotificationBell />
              <button
                type="button"
                className="h-11 rounded-2xl border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-rose-400/40 hover:bg-rose-500/10"
                onClick={() => {
                  void logout();
                }}
              >
                Cerrar sesion
              </button>
            </div>

            <div className="text-sm text-slate-300">
              <span className="font-medium text-white">{session.user?.name}</span>
              <span className="ml-2 text-slate-400">{session.user?.email}</span>
            </div>
            <div className="flex items-center gap-2 text-xs uppercase tracking-[0.2em] text-slate-400">
              <span
                className={[
                  "h-2.5 w-2.5 rounded-full",
                  realtimeStatus === "connected"
                    ? "bg-emerald-400"
                    : realtimeStatus === "connecting"
                      ? "bg-amber-400"
                      : realtimeStatus === "error"
                        ? "bg-rose-400"
                        : "bg-slate-500",
                ].join(" ")}
              />
              Tiempo real {realtimeStatus}
            </div>
          </div>
        </div>

        <div className="mx-auto max-w-7xl px-4 pb-4 sm:px-6">
          <nav className="flex flex-wrap gap-2">
            {navigationItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  [
                    "rounded-2xl px-4 py-2 text-sm font-medium transition",
                    isActive
                      ? "bg-brand-500 text-slate-950"
                      : "border border-white/10 bg-white/5 text-slate-200 hover:border-brand-400/40 hover:bg-brand-500/10",
                  ].join(" ")
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
      <NotificationToast />
    </div>
  );
}
