/**
 * Month ("period") helpers for the `?month=` / `?period=` API params
 * (framework-agnostic). A period is always "YYYY-MM".
 */

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const;

/** The device's current month as "YYYY-MM". */
export function currentPeriod(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Move a period by `delta` months: ("2026-01", -1) → "2025-12". */
export function shiftPeriod(period: string, delta: number): string {
  const [y, m] = period.split('-').map(Number);
  if (!y || !m) return period;
  const index = y * 12 + (m - 1) + delta;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}

/** "2026-06" → "June 2026" (or "Jun 2026" when `short`); the raw value if it can't parse. */
export function periodLabel(period: string | null | undefined, short = false): string {
  if (!period) return '—';
  const [y, m] = period.split('-').map(Number);
  if (!y || !m || m < 1 || m > 12) return period;
  const name = MONTH_NAMES[m - 1];
  return `${short ? name.slice(0, 3) : name} ${y}`;
}
