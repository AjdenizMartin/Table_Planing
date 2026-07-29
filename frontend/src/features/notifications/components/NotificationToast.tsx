import { useEffect, useState } from "react";

interface ToastData {
  id: string;
  title: string;
  message: string;
}

let toastListeners: ((toast: ToastData) => void)[] = [];

export function notify(title: string, message: string) {
  const toast: ToastData = {
    id: crypto.randomUUID(),
    title,
    message,
  };
  toastListeners.forEach((listener) => listener(toast));
}

export function NotificationToast() {
  const [toasts, setToasts] = useState<ToastData[]>([]);

  useEffect(() => {
    const listener = (toast: ToastData) => {
      setToasts((prev) => [...prev, toast]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== toast.id));
      }, 5000);
    };
    toastListeners.push(listener);
    return () => {
      toastListeners = toastListeners.filter((l) => l !== listener);
    };
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className="min-w-[280px] max-w-sm animate-slide-up rounded-lg border border-white/10 bg-slate-900 px-4 py-3 shadow-2xl"
        >
          <p className="text-sm font-medium text-white">{toast.title}</p>
          {toast.message && (
            <p className="mt-1 text-xs text-slate-400">{toast.message}</p>
          )}
        </div>
      ))}
    </div>
  );
}
