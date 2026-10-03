import React, { useEffect } from 'react';
import { View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { palette, radius } from '@/theme';
import { SPRING } from './motion';

type Props = {
  /** Zero-based current step. */
  index: number;
  total: number;
  variant?: 'bar' | 'dots';
  reduce: boolean;
};

const DOT = 6;
const DOT_ACTIVE = 18;

/**
 * The slim progress indicator in the Wizard header. `bar` springs its fill to
 * the current step; `dots` stretches the active dot into a short pill.
 * Decorative for screen readers — the header announces "Step n of N".
 */
export function WizardProgress({ index, total, variant = 'bar', reduce }: Props) {
  if (variant === 'dots') {
    return (
      <View
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 }}
      >
        {Array.from({ length: total }, (_, i) => (
          <Dot key={i} active={i === index} done={i < index} reduce={reduce} />
        ))}
      </View>
    );
  }
  return <Bar fraction={total <= 1 ? 1 : (index + 1) / total} reduce={reduce} />;
}

function Bar({ fraction, reduce }: { fraction: number; reduce: boolean }) {
  const trackW = useSharedValue(0);
  const p = useSharedValue(fraction);
  useEffect(() => {
    p.value = reduce ? withTiming(fraction, { duration: 0 }) : withSpring(fraction, SPRING);
  }, [fraction, reduce, p]);
  const fill = useAnimatedStyle(() => ({ width: Math.max(0, Math.min(1, p.value)) * trackW.value }));

  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      onLayout={(e) => {
        trackW.value = e.nativeEvent.layout.width;
      }}
      style={{ height: 5, borderRadius: radius.pill, backgroundColor: palette.line, overflow: 'hidden' }}
    >
      <Animated.View style={[{ height: '100%', borderRadius: radius.pill, backgroundColor: palette.coral }, fill]} />
    </View>
  );
}

function Dot({ active, done, reduce }: { active: boolean; done: boolean; reduce: boolean }) {
  const w = useSharedValue(active ? DOT_ACTIVE : DOT);
  useEffect(() => {
    const to = active ? DOT_ACTIVE : DOT;
    w.value = reduce ? to : withSpring(to, SPRING);
  }, [active, reduce, w]);
  const style = useAnimatedStyle(() => ({ width: w.value }));
  return (
    <Animated.View
      style={[
        { height: DOT, borderRadius: radius.pill, backgroundColor: active || done ? palette.coral : palette.line, opacity: done ? 0.45 : 1 },
        style,
      ]}
    />
  );
}
