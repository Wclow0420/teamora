/**
 * Type scale — Figtree everywhere (the Warm & Human direction's typeface).
 * Font family names map to the @expo-google-fonts/figtree weights loaded in
 * the root layout. Use the `font(weight)` helper or the named `type` presets;
 * never set fontFamily by hand.
 *
 * Leading rule: display/heading leading ≈ 1.2–1.3× the size (never ≈ 1.0 — that
 * clips descenders), body ≈ 1.5×. Negative tracking tightens large text the way
 * high-end product type does; positive tracking only on tiny all-caps eyebrows.
 */

export const fontFamily = {
  400: 'Figtree_400Regular',
  500: 'Figtree_500Medium',
  600: 'Figtree_600SemiBold',
  700: 'Figtree_700Bold',
  800: 'Figtree_800ExtraBold',
} as const;

export type FontWeight = keyof typeof fontFamily;

/** Returns the RN style fragment for a given weight (maps to the right family). */
export function font(weight: FontWeight) {
  return { fontFamily: fontFamily[weight] };
}

/** Named text presets. Prefer these over inline font sizes. */
export const type = {
  // Display / headings — leading opened up so descenders never clip.
  display: { ...font(800), fontSize: 30, lineHeight: 36, letterSpacing: -0.9 },
  h1: { ...font(800), fontSize: 22, lineHeight: 28, letterSpacing: -0.6 },
  h2: { ...font(700), fontSize: 19, lineHeight: 25, letterSpacing: -0.45 },
  h3: { ...font(700), fontSize: 17, lineHeight: 23, letterSpacing: -0.35 },
  title: { ...font(700), fontSize: 15, lineHeight: 20, letterSpacing: -0.2 },

  // Body
  body: { ...font(500), fontSize: 14, lineHeight: 21, letterSpacing: -0.1 },
  bodyStrong: { ...font(600), fontSize: 14, lineHeight: 20, letterSpacing: -0.1 },
  label: { ...font(600), fontSize: 13, lineHeight: 16, letterSpacing: -0.1 },
  meta: { ...font(500), fontSize: 12, lineHeight: 15 },
  micro: { ...font(600), fontSize: 11, lineHeight: 13 },

  // All-caps section eyebrow — the only place positive tracking belongs.
  eyebrow: {
    ...font(700),
    fontSize: 11,
    lineHeight: 13,
    letterSpacing: 1.1,
    textTransform: 'uppercase' as const,
  },

  // Money / timers — extra-bold, tight tracking, generous leading for big numerals.
  amount: { ...font(800), fontSize: 24, lineHeight: 30, letterSpacing: -0.6 },
} as const;

export type TypePreset = keyof typeof type;
