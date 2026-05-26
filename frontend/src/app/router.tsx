import { createBrowserRouter } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { ProtectedRoute } from "@/features/auth/components/ProtectedRoute";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { RestaurantSelectorPage } from "@/features/auth/pages/RestaurantSelectorPage";
import { CustomerDetailPage } from "@/features/frontdesk/pages/CustomerDetailPage";
import { CustomersPage } from "@/features/frontdesk/pages/CustomersPage";
import { ReservationsPage } from "@/features/frontdesk/pages/ReservationsPage";
import { HomePage } from "@/features/home/HomePage";
import { NotificationsPage } from "@/features/notifications/pages/NotificationsPage";
import { PlanningPage } from "@/features/planning/pages/PlanningPage";
import { DiningRoomsPage } from "@/features/restaurant-config/pages/DiningRoomsPage";
import { RestaurantSettingsPage } from "@/features/restaurant-config/pages/RestaurantSettingsPage";
import { TableCombinationsPage } from "@/features/restaurant-config/pages/TableCombinationsPage";
import { TableLayoutEditorPage } from "@/features/restaurant-config/pages/TableLayoutEditorPage";
import { TablesPage } from "@/features/restaurant-config/pages/TablesPage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    element: <ProtectedRoute allowWithoutRestaurant />,
    children: [
      {
        path: "/select-restaurant",
        element: <RestaurantSelectorPage />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          {
            index: true,
            element: <HomePage />,
          },
          {
            path: "customers",
            element: <CustomersPage />,
          },
          {
            path: "customers/:customerId",
            element: <CustomerDetailPage />,
          },
          {
            path: "reservations",
            element: <ReservationsPage />,
          },
          {
            path: "planning",
            element: <PlanningPage />,
          },
          {
            path: "notifications",
            element: <NotificationsPage />,
          },
          {
            path: "settings/restaurant",
            element: <RestaurantSettingsPage />,
          },
          {
            path: "settings/dining-rooms",
            element: <DiningRoomsPage />,
          },
          {
            path: "settings/tables",
            element: <TablesPage />,
          },
          {
            path: "settings/layout",
            element: <TableLayoutEditorPage />,
          },
          {
            path: "settings/table-combinations",
            element: <TableCombinationsPage />,
          },
        ],
      },
    ],
  },
]);
