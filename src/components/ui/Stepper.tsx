import React, { useEffect, useRef } from 'react';
import { View, Text, Pressable } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import * as Haptics from 'expo-haptics';
import { palette, radius, font, type, shadows } from '@/theme';
import { Icon } from './Icon';

type Props = {
  /** Row label on the left (e.g. "Hours per day"). */
  label?: string;
  /** Quiet second line under the label. */
  caption?: string;
  value: number;
  onChange: (value: number) => void;
  min: number;
  max: number;
  /** Increment per tap (default 1). Halves work: `step={0.5}`. */
  step?: number;
  /** How the number reads, e.g. `(n) => \`${n} min\``. Defaults to the plain number. */
  format?: (value: number) => string;
  disabled?: boolean;
};

/** Hold a button this long before it starts repeating, then repeat at this rate. */
const HOLD_DELAY_MS = 380;
const REPEAT_MS = 85;

/** Snap to the step grid and clamp — avoids 0.1 + 0.2 float drift on half steps. */
function snap(value: number, min: number, max: number, step: number): number {
  const snapped = Math.round((value - min) / step) * step + min;
  return Math.min(max, Math.max(min, Number(snapped.toFixed(4))));
}

/**
 * − value + control for small bounded numbers (hours per day, leave days, a
 * radius). Tabular numerals, a light selection haptic per change, press-and-hold
 * to run, and VoiceOver's adjustable gestures (swipe up / down).
 */
export function Stepper({ label, caption, value, onChange, min, max, step = 1, format, disabled = false }: Props) {
  const display = format ? format(value) : String(value);
  const atMin = value <= min;
  const atMax = value >= max;

  // Latest value for the hold-to-repeat timer, which outlives a render.
  const valueRef = useRef(value);
  valueRef.current = value;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Set once a hold has started repeating, so the release's onPress doesn't add one more.
  const repeated = useRef(false);

  // A tiny pop on the number whenever it changes.
  const pop = useSharedValue(1);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    pop.value = withSequence(withTiming(1.08, { duration: 70 }), withSpring(1, { damping: 14, stiffness: 320 }));
  }, [value, pop]);
  const popStyle = useAnimatedStyle(() => ({ transform: [{ scale: pop.value }] }));

  const bump = (dir: 1 | -1): boolean => {
    const next = snap(valueRef.current + dir * step, min, max, step);
    if (next === valueRef.current) return false;
    valueRef.current = next;
    onChange(next);
    Haptics.selectionAsync().catch(() => {});
    return true;
  };

  const stopRepeat = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  };
  useEffect(() => stopRepeat, []);

  const startRepeat = (dir: 1 | -1) => {
    stopRepeat();
    repeated.current = false;
    const tick = () => {
      repeated.current = true;
      if (!bump(dir)) return stopRepeat();
      timer.current = setTimeout(tick, REPEAT_MS);
    };
    timer.current = setTimeout(tick, HOLD_DELAY_MS);
  };

  const control = (dir: 1 | -1) => {
    const off = disabled || (dir === 1 ? atMax : atMin);
    return (
      <Pressable
        onPress={() => {
          if (repeated.current) repeated.current = false;
          else bump(dir);
        }}
        onPressIn={() => !off && startRepeat(dir)}
        onPressOut={stopRepeat}
        disabled={off}
        hitSlop={6}
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={({ pressed }) => [
          {
            width: 40,
            height: 40,
            borderRadius: radius.pill,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: pressed ? palette.surfaceSunken : palette.bg,
            borderWidth: 1,
            borderColor: palette.line,
            opacity: off ? 0.4 : 1,
            transform: [{ scale: pressed ? 0.94 : 1 }],
          },
        ]}
      >
        <Icon name={dir === 1 ? 'plus' : 'minus'} size={18} color={palette.ink} stroke={2.2} />
      </Pressable>
    );
  };

  return (
    <View
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={label}
      accessibilityValue={{ text: display }}
      accessibilityState={{ disabled }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(e) => {
        if (e.nativeEvent.actionName === 'increment') bump(1);
        if (e.nativeEvent.actionName === 'decrement') bump(-1);
      }}
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          gap: 12,
          minHeight: 64,
          paddingVertical: 12,
          paddingLeft: 16,
          paddingRight: 12,
          borderRadius: radius.lg,
          backgroundColor: palette.surface,
          borderWidth: 1,
          borderColor: palette.line,
        },
        shadows.card,
      ]}
    >
      <View style={{ flex: 1, minWidth: 0 }}>
        {!!label && <Text style={[type.bodyStrong, { color: palette.ink }]}>{label}</Text>}
        {!!caption && <Text style={[type.meta, { lineHeight: 17, color: palette.faint, marginTop: 3 }]}>{caption}</Text>}
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
        {control(-1)}
        <Animated.Text
          numberOfLines={1}
          style={[
            font(800),
            { minWidth: 62, textAlign: 'center', fontSize: 17, lineHeight: 22, letterSpacing: -0.3, color: palette.ink, fontVariant: ['tabular-nums'] },
            popStyle,
          ]}
        >
          {display}
        </Animated.Text>
        {control(1)}
      </View>
    </View>
  );
}
