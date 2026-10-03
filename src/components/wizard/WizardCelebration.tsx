import React, { useEffect } from 'react';
import { View, Text } from 'react-native';
import Animated, {
  Easing,
  interpolate,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import * as Haptics from 'expo-haptics';
import { Icon } from '@/components/ui';
import { palette, radius, shadows, tint, type } from '@/theme';
import { SPRING_POP, riseIn } from './motion';

export type CelebrationItem = {
  label: string;
  done: boolean;
  /** Short right-hand note, e.g. "12 added" or "Skipped". */
  detail?: string;
};

type Props = {
  title: string;
  subtitle?: string;
  items: CelebrationItem[];
};

const AnimatedPath = Animated.createAnimatedComponent(Path);

const MARK = 96; // coral circle
const STAGE = 200; // room for the burst around it
const CHECK_LEN = 20; // ≥ the check path's length, for the stroke-draw

/** Burst particles: fixed angles (no randomness → the same calm burst every time). */
const PARTICLES = [
  { a: -90, d: 84, s: 8, ring: false, c: palette.coral },
  { a: -55, d: 92, s: 7, ring: true, c: palette.amber },
  { a: -20, d: 80, s: 6, ring: false, c: palette.sage },
  { a: 15, d: 94, s: 9, ring: true, c: palette.violet },
  { a: 50, d: 82, s: 6, ring: false, c: palette.amber },
  { a: 88, d: 90, s: 7, ring: true, c: palette.coral },
  { a: 125, d: 84, s: 8, ring: false, c: palette.sage },
  { a: 160, d: 92, s: 6, ring: true, c: palette.amber },
  { a: 195, d: 80, s: 7, ring: false, c: palette.violet },
  { a: 230, d: 90, s: 9, ring: true, c: palette.sage },
  { a: 262, d: 86, s: 6, ring: false, c: palette.amber },
] as const;

/**
 * The finale for a Wizard step (use with `bare: true`). A coral circle springs
 * in, its check mark draws itself, a soft burst of dots and rings radiates and
 * fades, then the title and a checklist of what was done tick in one by one.
 * A success haptic lands with the check. Reduce Motion → a simple fade-in.
 */
export function WizardCelebration({ title, subtitle, items }: Props) {
  const reduce = useReducedMotion();
  const circle = useSharedValue(reduce ? 1 : 0);
  const check = useSharedValue(reduce ? 0 : CHECK_LEN);

  useEffect(() => {
    if (reduce) return;
    circle.value = withSpring(1, SPRING_POP);
    check.value = withDelay(260, withTiming(0, { duration: 420, easing: Easing.out(Easing.cubic) }));
  }, [reduce, circle, check]);

  useEffect(() => {
    const t = setTimeout(
      () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}),
      reduce ? 0 : 520,
    );
    return () => clearTimeout(t);
  }, [reduce]);

  const circleStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, circle.value * 1.6),
    transform: [{ scale: 0.3 + circle.value * 0.7 }],
  }));
  const checkProps = useAnimatedProps(() => ({ strokeDashoffset: check.value }));

  const base = reduce ? 0 : 640; // the text waits for the check

  return (
    <View>
      <View
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={{ height: STAGE, alignItems: 'center', justifyContent: 'center', marginTop: 4 }}
      >
        {!reduce && PARTICLES.map((p, i) => <Particle key={i} {...p} delay={200 + (i % 3) * 40} />)}
        <Animated.View
          style={[
            {
              width: MARK,
              height: MARK,
              borderRadius: radius.pill,
              backgroundColor: palette.coral,
              alignItems: 'center',
              justifyContent: 'center',
            },
            shadows.coral,
            circleStyle,
          ]}
        >
          <Svg width={48} height={48} viewBox="0 0 24 24">
            <AnimatedPath
              d="M5.5 12.5l4.3 4.3L18.5 8"
              fill="none"
              stroke={palette.white}
              strokeWidth={2.6}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray={[CHECK_LEN, CHECK_LEN]}
              animatedProps={checkProps}
            />
          </Svg>
        </Animated.View>
      </View>

      <Animated.Text
        entering={riseIn(base, reduce)}
        accessibilityRole="header"
        style={[type.display, { fontSize: 31, lineHeight: 38, color: palette.ink, textAlign: 'center' }]}
      >
        {title}
      </Animated.Text>
      {!!subtitle && (
        <Animated.Text
          entering={riseIn(base + 70, reduce)}
          style={[type.body, { fontSize: 15.5, lineHeight: 23, color: palette.soft, marginTop: 10, textAlign: 'center' }]}
        >
          {subtitle}
        </Animated.Text>
      )}

      <View
        style={[
          {
            marginTop: 26,
            borderRadius: radius['2xl'],
            backgroundColor: palette.surface,
            borderWidth: 1,
            borderColor: palette.line,
            overflow: 'hidden',
          },
          shadows.card,
        ]}
      >
        {items.map((item, i) => (
          <Animated.View
            key={item.label}
            entering={riseIn(base + 160 + i * 110, reduce)}
            accessible
            accessibilityLabel={`${item.label}: ${item.detail ?? (item.done ? 'done' : 'skipped')}`}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 12,
              paddingVertical: 13,
              paddingHorizontal: 16,
              borderTopWidth: i ? 1 : 0,
              borderTopColor: palette.line,
            }}
          >
            <View
              style={{
                width: 24,
                height: 24,
                borderRadius: radius.pill,
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: item.done ? tint.sage : tint.neutral,
              }}
            >
              {item.done ? (
                <Icon name="check" size={14} color={palette.sage} stroke={2.8} />
              ) : (
                <Icon name="minus" size={14} color={palette.faint} stroke={2.4} />
              )}
            </View>
            <Text style={[type.bodyStrong, { flex: 1, color: item.done ? palette.ink : palette.soft }]}>{item.label}</Text>
            {!!item.detail && (
              <Text style={[type.meta, { fontSize: 12.5, color: palette.faint, fontVariant: ['tabular-nums'] }]}>{item.detail}</Text>
            )}
          </Animated.View>
        ))}
      </View>
    </View>
  );
}

/** One burst particle: flies out from behind the mark, then fades. */
function Particle({ a, d, s, ring, c, delay }: { a: number; d: number; s: number; ring: boolean; c: string; delay: number }) {
  const t = useSharedValue(0);
  useEffect(() => {
    t.value = withDelay(delay, withTiming(1, { duration: 950, easing: Easing.out(Easing.cubic) }));
  }, [t, delay]);
  const rad = (a * Math.PI) / 180;
  const dx = Math.cos(rad) * d;
  const dy = Math.sin(rad) * d;
  const style = useAnimatedStyle(() => ({
    opacity: interpolate(t.value, [0, 0.12, 0.6, 1], [0, 1, 0.9, 0]),
    transform: [
      { translateX: dx * t.value },
      { translateY: dy * t.value },
      { scale: interpolate(t.value, [0, 0.3, 1], [0.4, 1, 0.8]) },
    ],
  }));
  return (
    <Animated.View
      style={[
        {
          position: 'absolute',
          width: s,
          height: s,
          borderRadius: radius.pill,
          backgroundColor: ring ? 'transparent' : c,
          borderWidth: ring ? 2 : 0,
          borderColor: c,
        },
        style,
      ]}
    />
  );
}
