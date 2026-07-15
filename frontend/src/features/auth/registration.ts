export function isRegistrationEnabled(value: string | undefined, production: boolean) {
  return !production && value !== "false";
}

export const registrationEnabled = isRegistrationEnabled(
  import.meta.env.VITE_REGISTRATION_ENABLED,
  import.meta.env.PROD,
);
