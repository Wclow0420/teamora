/**
 * Employee form field parsing + label helpers (framework-agnostic).
 *
 * The admin employee screens are split hub-and-spoke (one screen per category),
 * so the same few "text input → API value" parsers and the summary labels shown
 * on the hub tiles are shared from here instead of copied per screen.
 */

/** Parse a salary input → a non-negative number, or undefined if blank/invalid. */
export function parseSalary(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

/** Parse an hours-per-day input → a positive number, or undefined if blank/invalid. */
export function parseHours(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/** Parse a children-count input → a non-negative integer, or undefined if blank/invalid. */
export function parseChildren(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isInteger(n) && n >= 0 ? n : undefined;
}

/** "RM 4,000" — whole ringgit, grouped; sen only when they aren't zero. */
export function ringgit(value: number): string {
  const whole = Math.trunc(Math.abs(value));
  const sen = Math.round((Math.abs(value) - whole) * 100);
  const grouped = String(whole).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `RM ${grouped}${sen ? `.${String(sen).padStart(2, '0')}` : ''}`;
}

/** Join the parts of a hub-tile summary, dropping the ones with nothing to say. */
export function summaryLine(parts: (string | null | undefined)[]): string | null {
  const kept = parts.filter((p): p is string => !!p && p.trim().length > 0);
  return kept.length ? kept.join(' · ') : null;
}
