/**
 * Single import surface for the design system.
 *   import { theme } from '@/theme';
 *   theme.colors.coral, theme.type.h1, theme.radius.card, ...
 */
import { palette, tint, gradients, accents, accentTint } from './colors';
import { type, font, fontFamily } from './typography';
import { spacing, radius, touchTarget } from './spacing';
import { shadows } from './shadows';

export const theme = {
  colors: palette,
  tint,
  gradients,
  accents,
  accentTint,
  type,
  font,
  fontFamily,
  spacing,
  radius,
  touchTarget,
  shadows,
} as const;

export type Theme = typeof theme;

export { palette, tint, gradients, accents, accentTint } from './colors';
export { type, font, fontFamily } from './typography';
export type { FontWeight } from './typography';
export type { AccentKey } from './colors';
export { spacing, radius, touchTarget } from './spacing';
export { shadows } from './shadows';
