import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useI18n } from "@/features/i18n/I18nProvider";

interface ProtectedRouteProps {
  allowWithoutRestaurant?: boolean;
}

export function ProtectedRoute({ allowWithoutRestaurant = false }: ProtectedRouteProps) {
  const { status, session } = useAuth();
  const location = useLocation();
  const { t } = useI18n();

  if (status === "loading") {
    return (
      <main className="grid min-h-screen place-items-center bg-[#0c0f0e] px-6">
        <p className="text-sm font-medium text-slate-300">{t("Cargando...")}</p>
      </main>
    );
  }

  if (status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (
    !allowWithoutRestaurant &&
    session.restaurants.length > 1 &&
    session.activeRestaurantId === null
  ) {
    return <Navigate to="/select-restaurant" replace />;
  }

  return <Outlet />;
}
