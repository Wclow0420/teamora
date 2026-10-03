import type { HolidaySuggestion } from '@/api/types';

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2027-02-06" → "Sat, 6 Feb" (the raw value if it can't parse). */
export function holidayDateLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return iso;
  const date = new Date(y, m - 1, d);
  return `${WEEKDAYS[date.getDay()]}, ${d} ${MONTHS[m - 1]}`;
}

/**
 * Suggestions ticked by default: nationwide holidays not yet on the calendar.
 * State-specific ones (those with a `note`) are the admin's call.
 */
export function defaultHolidayTicks(items: HolidaySuggestion[]): string[] {
  return items.filter((h) => !h.alreadyAdded && !h.note).map((h) => h.date);
}
