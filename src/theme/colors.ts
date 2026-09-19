/**
 * Warm & Human palette — ported verbatim from the approved design direction.
 * Source of truth for every colour in the app. Never hard-code a hex in a
 * component; pull it from here so the whole system shifts in one place.
 */

export const palette = {
  // Surfaces
  bg: '#FBF5EE', // app background — warm cream
  surface: '#FFFFFF', // cards, sheets
  surfaceSunken: '#FAF3EB', // sunken subtotal / inset rows inside a card
  espresso: '#241C15', // dark hero surfaces (net pay, balance heroes)

  // Ink / text
  ink: '#2C2620', // primary text
  soft: '#6B6157', // secondary text
  faint: '#9A8F83', // tertiary / meta text
  inactive: '#B8AC9E', // disabled / inactive nav

  // Brand accents
  coral: '#EC6A4D',
  amber: '#F2A93B',
  sage: '#5C9070',
  violet: '#8268B5',
  danger: '#C2553F',

  // Lines
  line: '#EAE0D4',

  white: '#FFFFFF',
  black: '#000000',
} as const;

/**
 * Soft tint backgrounds paired with each accent (used behind icons, chips,
 * status pills). Keeping the accent → tint mapping here guarantees every
 * status badge across staff + admin reads consistently.
 */
export const tint = {
  coral: '#FDEEE7',
  amber: '#FCF1DE',
  sage: '#E9F2EC',
  violet: '#EFEAF6',
  neutral: '#EFEAE3',
  danger: '#FBE4DD',
} as const;

/**
 * Brand gradients. Used *sparingly and intentionally* — a gradient earns its
 * place only on a genuine "hero" surface (the attendance timer, the clock-in
 * backdrop). Everything else is confident flat colour + real elevation. Ranges
 * are kept tight (a single hue deepening), never wide multi-hue ramps — those
 * are the tell-tale sign of generic UI.
 */
export const gradients = {
  coral: ['#EE7355', '#E85F41'] as const, // tight coral deepen (hero only)
  coralCard: ['#EF7757', '#E85F41', '#DE5237'] as const, // attendance hero
  avatar: ['#F0906E', '#E85F41'] as const, // user avatars
  cream: ['#FDF3E9', '#F7E4D2'] as const, // pay hero (barely-there warmth)
  creamSoft: ['#FDF3E9', '#F6E2CE'] as const, // onboarding hero
  violet: ['#A88FD1', '#8268B5'] as const, // admin avatar
  clockBackdrop: ['#3A2A20', '#1E1813'] as const, // face-id radial backdrop
} as const;

/** Maps an accent key to its paired tint — handy for data-driven lists. */
export const accentTint: Record<string, string> = {
  [palette.coral]: tint.coral,
  [palette.amber]: tint.amber,
  [palette.sage]: tint.sage,
  [palette.violet]: tint.violet,
};

export type AccentKey = 'coral' | 'amber' | 'sage' | 'violet';

export const accents: Record<AccentKey, { color: string; tint: string }> = {
  coral: { color: palette.coral, tint: tint.coral },
  amber: { color: palette.amber, tint: tint.amber },
  sage: { color: palette.sage, tint: tint.sage },
  violet: { color: palette.violet, tint: tint.violet },
};
