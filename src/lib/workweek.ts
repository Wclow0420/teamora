/**
 * Working-weekday bitmask helpers (framework-agnostic).
 *
 * Bit layout: bit0=Mon, bit1=Tue, … bit6=Sun. Mon–Fri = 0b0011111 = 31;
 * full week = 0b1111111 = 127. Mirrors the backend `WorkWeek` contract so the
 * app can preview derived daily/hourly rates before payroll runs.
 */

export const WEEKDAYS_MASK = 31; // Mon–Fri
export const FULL_WEEK_MASK = 127; // Mon–Sun

/** Ordered weekday descriptors — index === bit position (Mon=0 … Sun=6). */
export const WEEKDAYS: { bit: number; short: string; label: string }[] = [
  { bit: 0, short: 'Mon', label: 'Monday' },
  { bit: 1, short: 'Tue', label: 'Tuesday' },
  { bit: 2, short: 'Wed', label: 'Wednesday' },
  { bit: 3, short: 'Thu', label: 'Thursday' },
  { bit: 4, short: 'Fri', label: 'Friday' },
  { bit: 5, short: 'Sat', label: 'Saturday' },
  { bit: 6, short: 'Sun', label: 'Sunday' },
];

/** Whether a given bit (0=Mon … 6=Sun) is set in the mask. */
export function isBitSet(mask: number, bit: number): boolean {
  return (mask & (1 << bit)) !== 0;
}

/** Toggle a weekday bit on/off, returning the new mask. */
export function toggleBit(mask: number, bit: number): number {
  return mask ^ (1 << bit);
}

/** Whether a JS weekday (0=Sun … 6=Sat, from `Date.getDay()`) is a working day. */
export function isWorkingDay(mask: number, jsDay: number): boolean {
  const bit = (jsDay + 6) % 7; // Sun(0)→6, Mon(1)→0, …
  return isBitSet(mask, bit);
}

/**
 * Count scheduled working days in a month.
 * @param mask working-weekday bitmask
 * @param year full year, e.g. 2026
 * @param month0 zero-based month (0=Jan … 11=Dec), matching `Date.getMonth()`
 */
export function scheduledWorkingDays(mask: number, year: number, month0: number): number {
  const daysInMonth = new Date(year, month0 + 1, 0).getDate();
  let count = 0;
  for (let d = 1; d <= daysInMonth; d++) {
    if (isWorkingDay(mask, new Date(year, month0, d).getDay())) count++;
  }
  return count;
}

/** Short human summary of a mask, e.g. "Mon–Fri", "Every day", or "Mon, Wed, Fri". */
export function describeMask(mask: number): string {
  if (mask === FULL_WEEK_MASK) return 'Every day';
  if (mask === WEEKDAYS_MASK) return 'Mon–Fri';
  const on = WEEKDAYS.filter((w) => isBitSet(mask, w.bit)).map((w) => w.short);
  return on.length ? on.join(', ') : 'No working days';
}

/**
 * Indicative daily & hourly rates derived from a monthly salary for a given month.
 * daily = salary / scheduledWorkingDays(month); hourly = daily / hoursPerDay.
 * Returns nulls when inputs make the rate undefined.
 */
export function deriveRates(
  monthlySalary: number | null | undefined,
  mask: number,
  hoursPerDay: number,
  year: number,
  month0: number,
): { scheduledDays: number; daily: number | null; hourly: number | null } {
  const scheduledDays = scheduledWorkingDays(mask, year, month0);
  if (monthlySalary == null || !Number.isFinite(monthlySalary) || scheduledDays <= 0) {
    return { scheduledDays, daily: null, hourly: null };
  }
  const daily = monthlySalary / scheduledDays;
  const hourly = hoursPerDay > 0 ? daily / hoursPerDay : null;
  return { scheduledDays, daily, hourly };
}
