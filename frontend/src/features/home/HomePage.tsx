import { useAuth } from "@/features/auth/context/AuthContext";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export function HomePage() {
  const { session } = useAuth();
  const activeRestaurant =
    session.restaurants.find(
      (restaurant) => restaurant.id === session.activeRestaurantId,
    ) ?? null;

  return (
    <section className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
      <div className="rounded-3xl border border-white/10 bg-white/5 p-8 shadow-2xl shadow-slate-950/40">
        <p className="mb-3 text-sm uppercase tracking-[0.25em] text-brand-300">
          Sesion Activa
        </p>
        <h2 className="max-w-2xl text-4xl font-semibold tracking-tight text-white">
          La autenticacion del frontend ya puede proteger rutas y fijar restaurante activo.
        </h2>
        <p className="mt-4 max-w-2xl text-base leading-7 text-slate-300">
          Esta pantalla ya vive dentro de una ruta protegida. Ya puedes moverte
          entre configuracion, clientes, reservas y planning usando el contexto
          del restaurante activo.
        </p>

        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
            <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
              Usuario
            </p>
            <p className="mt-2 text-lg font-semibold text-white">
              {session.user?.name}
            </p>
            <p className="mt-1 text-sm text-slate-400">{session.user?.email}</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
            <p className="text-xs uppercase tracking-[0.25em] text-slate-500">
              Restaurante activo
            </p>
            <p className="mt-2 text-lg font-semibold text-white">
              {activeRestaurant?.name ?? "Pendiente de seleccionar"}
            </p>
            <p className="mt-1 text-sm text-slate-400">
              {activeRestaurant?.roles.join(", ") ?? "Sin contexto seleccionado"}
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-3xl border border-white/10 bg-slate-900/70 p-6">
        <h3 className="text-lg font-semibold text-white">Entorno</h3>
        <dl className="mt-4 space-y-3 text-sm text-slate-300">
          <div>
            <dt className="text-slate-500">API base URL</dt>
            <dd className="mt-1 break-all text-brand-200">{apiBaseUrl}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Estado esperado del backend</dt>
            <dd className="mt-1">`/api/auth/me` con JWT y `X-Restaurant-Id`</dd>
          </div>
          <div>
            <dt className="text-slate-500">Siguiente modulo</dt>
            <dd className="mt-1">Iterar sobre drag and drop y asignacion avanzada</dd>
          </div>
        </dl>
      </div>
    </section>
  );
}
