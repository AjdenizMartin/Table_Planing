import {
  Armchair,
  CalendarDays,
  ChevronDown,
  CircleUserRound,
  Combine,
  LayoutDashboard,
  LogOut,
  Map,
  Settings,
  TableProperties,
  Users,
  Utensils,
  type LucideIcon,
} from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useRealtime } from "@/features/realtime/RealtimeProvider";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { NotificationToast } from "@/features/notifications/components/NotificationToast";
import { useI18n } from "@/features/i18n/I18nProvider";

interface NavigationItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

interface NavigationGroup {
  label: string;
  items: NavigationItem[];
}

const navigationGroups: NavigationGroup[] = [
  {
    label: "Operations",
    items: [
      { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
      { to: "/planning", label: "Planning", icon: CalendarDays },
      { to: "/reservations", label: "Reservations", icon: TableProperties },
      { to: "/customers", label: "Customers", icon: Users },
    ],
  },
  {
    label: "Settings",
    items: [
      { to: "/settings/restaurant", label: "Restaurant", icon: Utensils },
      { to: "/settings/dining-rooms", label: "Dining rooms", icon: Armchair },
      { to: "/settings/tables", label: "Tables", icon: TableProperties },
      { to: "/settings/layout", label: "Floor plan", icon: Map },
      {
        to: "/settings/table-combinations",
        label: "Combinations",
        icon: Combine,
      },
    ],
  },
];

const mobileItems = [
  navigationGroups[0].items[0],
  navigationGroups[0].items[1],
  navigationGroups[0].items[2],
  navigationGroups[0].items[3],
  { to: "/settings/restaurant", label: "Settings", icon: Settings },
];

export function AppLayout() {
  const { logout, session, setActiveRestaurantId } = useAuth();
  const { status: realtimeStatus } = useRealtime();
  const { t } = useI18n();
  const activeRestaurant =
    session.restaurants.find(
      (restaurant) => restaurant.id === session.activeRestaurantId,
    ) ?? null;

  const realtimeLabel =
    realtimeStatus === "connected"
      ? t("Live updates connected")
      : realtimeStatus === "connecting"
        ? t("Connecting live updates")
        : realtimeStatus === "error"
          ? t("Live updates unavailable")
          : t("Live updates disconnected");

  return (
    <div className="min-h-screen bg-[#0c0f0e] lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
      <aside className="hidden border-r border-white/8 bg-[#101412] lg:sticky lg:top-0 lg:flex lg:h-screen lg:flex-col">
        <div className="flex h-16 items-center gap-3 border-b border-white/8 px-5">
          <div className="grid h-9 w-9 place-items-center rounded-lg bg-emerald-400 text-sm font-black text-[#0b110e]">
            TP
          </div>
          <div>
            <p className="text-sm font-semibold text-white">Table Planning</p>
            <p className="text-xs text-slate-500">Restaurant OS</p>
          </div>
        </div>

        <nav className="min-h-0 flex-1 overflow-y-auto px-3 py-5">
          {navigationGroups.map((group) => (
            <div key={group.label} className="mb-6">
              <p className="mb-2 px-3 text-[11px] font-semibold uppercase text-slate-500">
                {t(group.label)}
              </p>
              <div className="grid gap-1">
                {group.items.map((item) => (
                  <NavigationLink key={item.to} item={item} label={t(item.label)} />
                ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-white/8 p-3">
          <div className="flex items-center gap-3 rounded-lg px-3 py-2">
            <CircleUserRound className="h-8 w-8 text-slate-400" strokeWidth={1.5} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-white">
                {session.user?.name}
              </p>
              <p className="truncate text-xs text-slate-500">{session.user?.email}</p>
            </div>
            <button
              type="button"
              className="grid h-9 w-9 place-items-center rounded-lg text-slate-400 transition hover:bg-rose-500/10 hover:text-rose-300"
              title={t("Sign out")}
              aria-label={t("Sign out")}
              onClick={() => void logout()}
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-40 flex h-16 items-center border-b border-white/8 bg-[#0c0f0e]/95 px-4 backdrop-blur sm:px-6">
          <div className="flex min-w-0 items-center gap-3 lg:hidden">
            <div className="grid h-9 w-9 place-items-center rounded-lg bg-emerald-400 text-xs font-black text-[#0b110e]">
              TP
            </div>
            <span className="hidden text-sm font-semibold text-white sm:inline">
              Table Planning
            </span>
          </div>

          <div className="ml-auto flex min-w-0 items-center gap-2 sm:gap-3">
            {session.restaurants.length > 1 ? (
              <label className="relative hidden sm:block">
                <span className="sr-only">{t("Active restaurant")}</span>
                <select
                  className="h-9 max-w-56 appearance-none rounded-lg border border-white/10 bg-white/5 py-0 pl-3 pr-9 text-sm text-white outline-none focus:border-emerald-400/60"
                  value={session.activeRestaurantId ?? ""}
                  onChange={(event) => setActiveRestaurantId(Number(event.target.value))}
                >
                  <option value="" disabled>
                    {t("Select")}
                  </option>
                  {session.restaurants.map((restaurant) => (
                    <option key={restaurant.id} value={restaurant.id}>
                      {restaurant.name}
                    </option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-2.5 h-4 w-4 text-slate-500" />
              </label>
            ) : (
              <span className="hidden max-w-48 truncate text-sm font-medium text-slate-300 sm:block">
                {activeRestaurant?.name}
              </span>
            )}

            <div
              className="hidden items-center gap-2 text-xs text-slate-500 md:flex"
              title={realtimeLabel}
            >
              <span
                className={[
                  "h-2 w-2 rounded-full",
                  realtimeStatus === "connected"
                    ? "bg-emerald-400"
                    : realtimeStatus === "connecting"
                      ? "bg-amber-400"
                      : "bg-rose-400",
                ].join(" ")}
              />
              <span className="sr-only">{realtimeLabel}</span>
            </div>

            <NotificationBell />
            <button
              type="button"
              className="grid h-9 w-9 place-items-center rounded-lg text-slate-400 transition hover:bg-white/5 hover:text-white lg:hidden"
              title={t("Sign out")}
              aria-label={t("Sign out")}
              onClick={() => void logout()}
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </header>

        <main className="mx-auto min-h-[calc(100vh-4rem)] w-full min-w-0 max-w-[1600px] px-4 py-5 pb-24 sm:px-6 sm:py-6 lg:pb-8">
          <Outlet />
        </main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-50 grid grid-cols-5 border-t border-white/10 bg-[#101412]/98 px-1 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-1 backdrop-blur lg:hidden">
        {mobileItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={"end" in item ? item.end : false}
              className={({ isActive }) =>
                [
                  "flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-2 text-[10px] font-medium transition",
                  isActive ? "text-emerald-300" : "text-slate-500",
                ].join(" ")
              }
            >
              <Icon className="h-5 w-5" strokeWidth={1.8} />
              <span className="max-w-full truncate">{t(item.label)}</span>
            </NavLink>
          );
        })}
      </nav>

      <NotificationToast />
    </div>
  );
}

function NavigationLink({ item, label }: { item: NavigationItem; label: string }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        [
          "flex h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium transition",
          isActive
            ? "bg-emerald-400/12 text-emerald-200"
            : "text-slate-400 hover:bg-white/5 hover:text-white",
        ].join(" ")
      }
    >
      <Icon className="h-[18px] w-[18px]" strokeWidth={1.8} />
      <span>{label}</span>
    </NavLink>
  );
}
