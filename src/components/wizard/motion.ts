import {
  ReduceMotion,
  withDelay,
  withSpring,
  withTiming,
  type EntryAnimationsValues,
  type ExitAnimationsValues,
  type LayoutAnimation,
  type SharedValue,
} from 'react-native-reanimated';

/**
 * Motion vocabulary for the Wizard kit — one place, so every step moves the same
 * way. Calm and quick: springs settle in ~300–450 ms with almost no overshoot;
 * only the hero gets a hint of bounce.
 *
 * Reduce Motion: every builder takes `reduce`. When true we drop all travel and
 * scale and keep a short crossfade (explicitly `ReduceMotion.Never` on the fade,
 * otherwise Reanimated would skip it entirely and steps would hard-cut).
 */

/** Step content + progress — critically-damped feel, settles ≈ 350 ms. */
export const SPRING = { damping: 26, stiffness: 240, mass: 1 } as const;
/** Hero icon pop — a touch of overshoot, settles ≈ 450 ms. */
export const SPRING_POP = { damping: 14, stiffness: 190, mass: 0.9 } as const;

/** How far a step travels sideways on enter/exit (px). Small = calm. */
const STEP_SHIFT = 56;
/** How far body content rises while fading in (px). */
const RISE = 14;

const FADE = { duration: 220, reduceMotion: ReduceMotion.Never } as const;

/**
 * Incoming step. `dir` is +1 forward (enters from the right), −1 back (from the
 * left), 0 for the first mount (no travel — the screen push already moved it).
 * Read from a shared value at animation time, so direction is always current.
 */
export function stepEntering(dir: SharedValue<number>, reduce: boolean) {
  return (_values: EntryAnimationsValues): LayoutAnimation => {
    'worklet';
    if (reduce) {
      return { initialValues: { opacity: 0 }, animations: { opacity: withTiming(1, FADE) } };
    }
    return {
      initialValues: { opacity: 0, transform: [{ translateX: dir.value * STEP_SHIFT }] },
      animations: {
        opacity: withTiming(1, { duration: 260 }),
        transform: [{ translateX: withSpring(0, SPRING) }],
      },
    };
  };
}

/** Outgoing step — drifts the opposite way and fades, a little faster than the incoming one. */
export function stepExiting(dir: SharedValue<number>, reduce: boolean) {
  return (_values: ExitAnimationsValues): LayoutAnimation => {
    'worklet';
    if (reduce) {
      return { initialValues: { opacity: 1 }, animations: { opacity: withTiming(0, { ...FADE, duration: 160 }) } };
    }
    return {
      initialValues: { opacity: 1, transform: [{ translateX: 0 }] },
      animations: {
        opacity: withTiming(0, { duration: 170 }),
        transform: [{ translateX: withTiming(-dir.value * STEP_SHIFT * 0.6, { duration: 220 }) }],
      },
    };
  };
}

/** Title / subtitle / body: fade + small rise, delayed for the stagger. */
export function riseIn(delay: number, reduce: boolean) {
  return (_values: EntryAnimationsValues): LayoutAnimation => {
    'worklet';
    if (reduce) {
      return { initialValues: { opacity: 0 }, animations: { opacity: withTiming(1, FADE) } };
    }
    return {
      initialValues: { opacity: 0, transform: [{ translateY: RISE }] },
      animations: {
        opacity: withDelay(delay, withTiming(1, { duration: 280 })),
        transform: [{ translateY: withDelay(delay, withSpring(0, SPRING)) }],
      },
    };
  };
}

/** Hero icon: 0.6 → 1 spring with a whisper of overshoot, plus fade. */
export function popIn(delay: number, reduce: boolean) {
  return (_values: EntryAnimationsValues): LayoutAnimation => {
    'worklet';
    if (reduce) {
      return { initialValues: { opacity: 0 }, animations: { opacity: withTiming(1, FADE) } };
    }
    return {
      initialValues: { opacity: 0, transform: [{ scale: 0.6 }] },
      animations: {
        opacity: withDelay(delay, withTiming(1, { duration: 220 })),
        transform: [{ scale: withDelay(delay, withSpring(1, SPRING_POP)) }],
      },
    };
  };
}

/** Stagger offsets (ms) for hero → title → subtitle → body. 60–90 ms apart. */
export const STAGGER = { hero: 40, title: 120, subtitle: 190, body: 260 } as const;
