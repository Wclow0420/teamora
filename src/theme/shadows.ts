import { Platform } from 'react-native';

/**
 * Warm-toned elevation. The prototype's shadows are brown-tinted and very
 * soft. RN doesn't support the CSS spread/blur model directly, so these are
 * tuned approximations that read the same on device.
 */

type Shadow = {
  shadowColor: string;
  shadowOpacity: number;
  shadowRadius: number;
  shadowOffset: { width: number; height: number };
  elevation: number;
};

function make(color: string, opacity: number, radius: number, y: number, elevation: number): Shadow {
  return {
    shadowColor: color,
    shadowOpacity: opacity,
    shadowRadius: radius,
    shadowOffset: { width: 0, height: y },
    elevation,
  };
}

const BROWN = '#5C3620'; // deep warm base — reads as a soft brown shadow, not grey

export const shadows = {
  /** Resting card elevation — soft, wide, low-opacity (ambient, not a hard drop). */
  card: make(BROWN, 0.07, 16, 7, 2),
  /** Floating pill nav / prominent cards — a touch more lift, still diffuse. */
  float: make(BROWN, 0.12, 28, 14, 8),
  /** Primary CTA — a restrained brand-tinted ambient lift (NOT a neon glow). */
  coral: make('#C4432B', 0.22, 16, 8, 6),
  /** No elevation (explicit reset). */
  none: Platform.select({
    ios: make('#000', 0, 0, 0, 0),
    default: { elevation: 0 } as Shadow,
  })!,
} as const;
