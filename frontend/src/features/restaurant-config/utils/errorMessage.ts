import { ApiError } from "@/services/api/client";
import type { ApiErrorPayload } from "@/features/restaurant-config/types";

export function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const details = error.details as ApiErrorPayload | undefined;
    if (details?.details?.length) {
      return details.details
        .map((detail) => `${detail.field}: ${detail.message}`)
        .join(" · ");
    }

    if (details?.message) {
      return details.message;
    }

    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "No se pudo completar la operacion.";
}

