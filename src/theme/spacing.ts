/**
 * Spacing + radius scales. The prototype uses generous, soft geometry — large
 * corner radii and roomy padding. These tokens keep that consistent.
 */

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  '2xl': 24,
  '3xl': 28,
  screenX: 20, // default horizontal screen padding (matches prototype)
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 18,
  '2xl': 20,
  '3xl': 24,
  card: 20,
  hero: 28,
  pill: 999,
} as const;

/** Standard hit target for tappable rows / icon buttons. */
export const touchTarget = 44;
