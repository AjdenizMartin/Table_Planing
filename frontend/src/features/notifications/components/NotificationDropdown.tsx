import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import {
  useNotifications,
  useMarkAsRead,
  useMarkAllAsRead,
} from "@/features/notifications/hooks/useNotifications";

interface NotificationDropdownProps {
  onClose: () => void;
}

export function NotificationDropdown({ onClose }: NotificationDropdownProps) {
  const navigate = useNavigate();
  const ref = useRef<HTMLDivElement>(null);
  const { data: notifications = [] } = useNotifications(true);
  const markAsRead = useMarkAsRead();
  const markAllAsRead = useMarkAllAsRead();

  useEffect(() => {
    function handleClick(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [onClose]);

  function handleClick(notificationId: number) {
    markAsRead.mutate(notificationId);
    onClose();
  }

  return (
    <div
      ref={ref}
      className="absolute right-0 top-full z-50 mt-2 w-80 rounded-2xl border border-white/10 bg-slate-900 shadow-2xl"
    >
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <span className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
          Notificaciones
        </span>
        {notifications.length > 0 && (
          <button
            type="button"
            className="text-xs text-brand-400 hover:text-brand-300"
            onClick={() => {
              markAllAsRead.mutate();
            }}
          >
            Marcar todas leidas
          </button>
        )}
      </div>
      <div className="max-h-80 overflow-y-auto">
        {notifications.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-slate-500">
            Sin notificaciones
          </p>
        ) : (
          notifications.slice(0, 20).map((notification) => (
            <button
              key={notification.id}
              type="button"
              className="flex w-full flex-col gap-1 border-b border-white/5 px-4 py-3 text-left transition hover:bg-white/5"
              onClick={() => handleClick(notification.id)}
            >
              <span className="text-sm font-medium text-white">
                {notification.title}
              </span>
              {notification.body && (
                <span className="line-clamp-2 text-xs text-slate-400">
                  {notification.body}
                </span>
              )}
              <span className="text-[10px] text-slate-500">
                {new Date(notification.createdAt).toLocaleString()}
              </span>
            </button>
          ))
        )}
      </div>
      <div className="border-t border-white/10 px-4 py-2">
        <button
          type="button"
          className="w-full rounded-xl py-2 text-sm text-brand-400 transition hover:bg-white/5"
          onClick={() => {
            navigate("/notifications");
            onClose();
          }}
        >
          Ver todas
        </button>
      </div>
    </div>
  );
}
