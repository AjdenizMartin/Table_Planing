import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/context/AuthContext";

interface ProtectedRouteProps {
  allowWithoutRestaurant?: boolean;
}

export function ProtectedRoute({ allowWithoutRestaurant = false }: ProtectedRouteProps) {
  const { status, session } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <div className="w-full max-w-md rounded-3xl border border-white/10 bg-slate-950/70 p-8 text-center shadow-2xl shadow-black/30">
          <p className="text-sm uppercase tracking-[0.3em] text-brand-300">
            Restaurant Table Planning
          </p>
          <h1 className="mt-4 text-2xl font-semibold text-white">
            Restaurando sesion
          </h1>
          <p className="mt-3 text-sm text-slate-300">
            Comprobando credenciales y contexto de restaurante.
          </p>
        </div>
      </div>
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

