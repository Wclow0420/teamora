import { isWorkingDay } from './workweek';

/**
 * Client-side estimate of how many leave days a request costs
 * (framework-agnostic). The server's `LeaveDurationCalculator` is the source of
 * truth; this mirrors its rules so the form can warn before a round-trip.
 */

/** Longest full-day range we'll count locally — beyond it, the server decides. */
const MAX_LOCAL_RANGE_DAYS = 62;

/** "2026-06" for a date (local time). */
export function monthKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** "2026-06-09" for a date (local time) — the key used for holiday lookups. */
export function dayKey(d: Date): string {
  return `${monthKey(d)}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Scheduled working days in `[start, end]`: rest days (per the work-week mask)
 * and public holidays are skipped, exactly like the server. Returns null when
 * the range is invalid or too long to count locally.
 */
export function workingDaysInRange(start: Date, end: Date, mask: number, holidays: ReadonlySet<string>): number | null {
  const from = new Date(start.getFullYear(), start.getMonth(), start.getDate());
  const to = new Date(end.getFullYear(), end.getMonth(), end.getDate());
  if (to < from) return null;
  let count = 0;
  let scanned = 0;
  for (const d = from; d <= to; d.setDate(d.getDate() + 1)) {
    if (++scanned > MAX_LOCAL_RANGE_DAYS) return null;
    if (isWorkingDay(mask, d.getDay()) && !holidays.has(dayKey(d))) count++;
  }
  return count;
}
