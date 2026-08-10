import { useState } from "react";
import { useUnreadCount } from "@/features/notifications/hooks/useNotifications";
import { NotificationDropdown } from "@/features/notifications/components/NotificationDropdown";
import { Bell } from "lucide-react";
import { useI18n } from "@/features/i18n/I18nProvider";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const { t } = useI18n();
  const { data: count = 0 } = useUnreadCount();

  return (
    <div className="relative">
      <button
        type="button"
        className="relative h-11 rounded-lg border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
        onClick={() => setOpen(!open)}
        aria-label={t("Notifications")}
      >
        <Bell className="h-5 w-5" />
        {count > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-rose-500 text-[10px] font-bold text-white">
            {count > 99 ? "99+" : count}
          </span>
        )}
      </button>
      {open && <NotificationDropdown onClose={() => setOpen(false)} />}
    </div>
  );
}
