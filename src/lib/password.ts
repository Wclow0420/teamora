/** Server password rules, mirrored client-side so people hear about them before a round-trip. */
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 72;

/** Helper line shown under every "choose a password" field. */
export const PASSWORD_HELPER = `At least ${MIN_PASSWORD_LENGTH} characters.`;

/** The inline error for a chosen password, or null when it's acceptable. */
export function passwordError(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  if (password.length > MAX_PASSWORD_LENGTH) return `Keep it to ${MAX_PASSWORD_LENGTH} characters or fewer.`;
  return null;
}
