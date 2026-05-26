import { useAuth } from "@/features/auth/context/AuthContext";

export function useActiveRestaurant() {
  const { session } = useAuth();

  const activeRestaurant =
    session.restaurants.find(
      (restaurant) => restaurant.id === session.activeRestaurantId,
    ) ?? null;

  return {
    activeRestaurantId: session.activeRestaurantId,
    activeRestaurant,
  };
}

