import {
  CalendarPlus,
  Map,
  Search,
  Sparkles,
  Users,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/features/i18n/I18nProvider";

const quickActions = [
  {
    to: "/planning",
    label: "Open planning",
    icon: Sparkles,
    color: "text-emerald-300 bg-emerald-400/10",
  },
  {
    to: "/reservations",
    label: "New reservation",
    icon: CalendarPlus,
    color: "text-sky-300 bg-sky-400/10",
  },
  {
    to: "/customers",
    label: "Find customer",
    icon: Search,
    color: "text-amber-300 bg-amber-400/10",
  },
  {
    to: "/settings/layout",
    label: "Configure dining room",
    icon: Map,
    color: "text-violet-300 bg-violet-400/10",
  },
];

export function HomePage() {
  const { session } = useAuth();
  const { t } = useI18n();
  const activeRestaurant =
    session.restaurants.find(
      (restaurant) => restaurant.id === session.activeRestaurantId,
    ) ?? null;

  return (
    <section className="grid gap-6">
      <header className="flex flex-col gap-2 border-b border-white/8 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm text-slate-500">{t("Today")}</p>
          <h1 className="mt-1 text-2xl font-semibold text-white sm:text-3xl">
            {activeRestaurant?.name ?? t("No restaurant selected")}
          </h1>
        </div>
        <div className="flex items-center gap-3 text-sm text-slate-400">
          <Users className="h-4 w-4" />
          <span>{session.user?.name}</span>
        </div>
      </header>

      <div>
        <h2 className="text-sm font-semibold text-slate-300">
          {t("Quick actions")}
        </h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {quickActions.map((action) => {
            const Icon = action.icon;
            return (
              <Link
                key={action.to}
                to={action.to}
                className="group flex min-h-24 items-center gap-4 rounded-lg border border-white/8 bg-[#111614] p-4 transition hover:border-white/15 hover:bg-[#151b18]"
              >
                <span className={`grid h-10 w-10 place-items-center rounded-lg ${action.color}`}>
                  <Icon className="h-5 w-5" />
                </span>
                <span className="text-sm font-semibold text-white">
                  {t(action.label)}
                </span>
              </Link>
            );
          })}
        </div>
      </div>
    </section>
  );
}
