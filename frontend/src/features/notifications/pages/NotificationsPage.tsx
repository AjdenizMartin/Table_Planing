import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  useNotifications,
  useMarkAsRead,
  useMarkAllAsRead,
} from "@/features/notifications/hooks/useNotifications";
import type { Notification } from "@/features/notifications/types";
import { useI18n } from "@/features/i18n/I18nProvider";

export function NotificationsPage() {
  const navigate = useNavigate();
  const { t } = useI18n();
  const [showRead, setShowRead] = useState(false);
  const { data: unread = [] } = useNotifications(true);
  const { data: all = [] } = useNotifications(false);
  const markAsRead = useMarkAsRead();
  const markAllAsRead = useMarkAllAsRead();

  const notifications = showRead ? all : unread;

  function handleNotificationClick(notification: Notification) {
    if (!notification.read) {
      markAsRead.mutate(notification.id);
    }
    if (notification.entityType === "Reservation" && notification.entityId) {
      navigate(`/reservations?highlight=${notification.entityId}`);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-white">{t("Notifications")}</h1>
        <button
          type="button"
          className="h-11 rounded-lg border border-white/10 bg-white/5 px-4 text-sm font-medium text-white transition hover:border-brand-400/40 hover:bg-brand-500/10"
          onClick={() => setShowRead(!showRead)}
        >
          {showRead ? t("Unread only") : t("Show all")}
        </button>
      </div>

      {notifications.length > 0 && (
        <div className="mb-4 flex justify-end">
          <button
            type="button"
            className="rounded-lg px-4 py-2 text-sm text-brand-400 transition hover:bg-white/5"
            onClick={() => markAllAsRead.mutate()}
          >
            {t("Mark all as read")}
          </button>
        </div>
      )}

      <div className="flex flex-col gap-2">
        {notifications.length === 0 ? (
          <div className="rounded-lg border border-white/10 bg-white/5 px-6 py-12 text-center">
            <p className="text-lg text-slate-400">
              {showRead ? t("There are no notifications") : t("There are no unread notifications")}
            </p>
          </div>
        ) : (
          notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              className={`flex flex-col gap-1 rounded-lg border px-5 py-4 text-left transition hover:bg-white/5 ${
                notification.read
                  ? "border-white/5 bg-white/[0.02]"
                  : "border-brand-400/20 bg-brand-500/[0.04]"
              }`}
              onClick={() => handleNotificationClick(notification)}
            >
              <div className="flex items-center gap-2">
                {!notification.read && (
                  <span className="h-2 w-2 rounded-full bg-brand-400" />
                )}
                <span className="text-sm font-medium text-white">
                  {notification.title}
                </span>
                <span className="ml-auto text-[10px] text-slate-500">
                  {new Date(notification.createdAt).toLocaleString()}
                </span>
              </div>
              {notification.body && (
                <p className="text-xs text-slate-400">{notification.body}</p>
              )}
            </button>
          ))
        )}
      </div>
    </div>
  );
}
