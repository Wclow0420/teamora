import { palette, tint } from '@/theme';

export type Accent = { color: string; bg: string };

/** Backend `accentColorKey` ("sage"|"violet"|"amber"|"coral") → theme accent. */
export function accentFromKey(key: string | null | undefined): Accent {
  switch (key) {
    case 'sage':
      return { color: palette.sage, bg: tint.sage };
    case 'violet':
      return { color: palette.violet, bg: tint.violet };
    case 'amber':
      return { color: palette.amber, bg: tint.amber };
    case 'coral':
      return { color: palette.coral, bg: tint.coral };
    default:
      return { color: palette.soft, bg: tint.neutral };
  }
}

/** Request/claim status → accent. APPROVED→sage, PENDING→amber, REJECTED→danger. */
export function statusAccent(status: string | null | undefined): Accent {
  switch ((status ?? '').toUpperCase()) {
    case 'APPROVED':
      return { color: palette.sage, bg: tint.sage };
    case 'PENDING':
      return { color: palette.amber, bg: tint.amber };
    case 'REJECTED':
      return { color: palette.danger, bg: tint.danger };
    default:
      return { color: palette.soft, bg: tint.neutral };
  }
}
