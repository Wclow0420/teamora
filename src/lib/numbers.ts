/**
 * Number formatting helpers (framework-agnostic).
 *
 * Leave is fractional (half days, hourly slices), so balances and day counts
 * arrive as numbers like 12.5 or 0.25. Rendering those raw can produce
 * "12.5000" / floating-point noise — always format through here.
 */

/**
 * Format a number to at most `maxFractionDigits` decimals, trimming trailing
 * zeros: 12.5 → "12.5", 16 → "16", 0.25 → "0.25", 0.3333 → "0.33".
 */
export function formatDecimal(value: number | null | undefined, maxFractionDigits = 2): string {
  if (value == null || !Number.isFinite(value)) return '0';
  const dp = Math.max(0, Math.min(10, Math.trunc(maxFractionDigits)));
  const fixed = value.toFixed(dp);
  if (!fixed.includes('.')) return fixed;
  return fixed.replace(/0+$/, '').replace(/\.$/, '');
}

/** "1 day" / "0.5 days" / "3 days" — pluralised, trailing zeros trimmed. */
export function formatDays(value: number | null | undefined): string {
  const n = value == null || !Number.isFinite(value) ? 0 : value;
  return `${formatDecimal(n)} ${n === 1 ? 'day' : 'days'}`;
}

/** "1 hour" / "2 hours" / "1.5 hours". */
export function formatHours(value: number | null | undefined): string {
  const n = value == null || !Number.isFinite(value) ? 0 : value;
  return `${formatDecimal(n)} ${n === 1 ? 'hour' : 'hours'}`;
}
