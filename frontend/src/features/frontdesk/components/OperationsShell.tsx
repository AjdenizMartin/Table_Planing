import { NavLink } from "react-router-dom";
import type { PropsWithChildren } from "react";
import { useActiveRestaurant } from "@/features/restaurant-config/hooks/useActiveRestaurant";

const sections = [
  { to: "/customers", label: "Clientes" },
  { to: "/reservations", label: "Reservas" },
];

export function OperationsShell({
  title,
  description,
  children,
}: PropsWithChildren<{ title: string; description: string }>) {
  const { activeRestaurant } = useActiveRestaurant();

  return (
    <section className="grid min-w-0 gap-6 lg:grid-cols-[260px_minmax(0,1fr)]">
      <aside className="min-w-0 rounded-[2rem] border border-white/10 bg-slate-950/65 p-5 shadow-2xl shadow-black/20">
        <p className="text-xs uppercase tracking-[0.3em] text-brand-300">
          Operacion
        </p>
        <h2 className="mt-3 text-2xl font-semibold text-white">
          {activeRestaurant?.name ?? "Restaurante activo"}
        </h2>
        <p className="mt-2 text-sm leading-6 text-slate-400">
          Gestiona clientes y reservas manuales con una interfaz rapida para tablet.
        </p>

        <nav className="mt-6 grid gap-2">
          {sections.map((section) => (
            <NavLink
              key={section.to}
              to={section.to}
              className={({ isActive }) =>
                [
                  "rounded-2xl px-4 py-3 text-sm font-medium transition",
                  isActive
                    ? "bg-brand-500 text-slate-950"
                    : "border border-white/10 bg-white/5 text-slate-200 hover:border-brand-400/40 hover:bg-brand-500/10",
                ].join(" ")
              }
            >
              {section.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="grid min-w-0 gap-6">
        <header className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-2xl shadow-black/20 sm:p-8">
          <p className="text-xs uppercase tracking-[0.3em] text-brand-300">
            Front Desk
          </p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            {title}
          </h1>
          <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300 sm:text-base">
            {description}
          </p>
        </header>

        {children}
      </div>
    </section>
  );
}
