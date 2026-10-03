import React, { useEffect } from 'react';
import { View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { LinearGradient } from 'expo-linear-gradient';
import { gradients, radius, shadows, tint } from '@/theme';

const MARK = 76;
const HALO = 124;

/**
 * The Teamora mark as a wizard hero: the brand tile (the same coral square as
 * the wordmark on Login) resting on a soft halo that breathes slowly. The
 * Wizard springs the whole thing in; this only adds the glow. Reduce Motion →
 * a still halo.
 */
export function WelcomeMark() {
  const reduce = useReducedMotion();
  const breath = useSharedValue(0);
  useEffect(() => {
    if (reduce) return;
    breath.value = withRepeat(withTiming(1, { duration: 2400, easing: Easing.inOut(Easing.sin) }), -1, true);
  }, [reduce, breath]);
  const halo = useAnimatedStyle(() => ({
    opacity: 0.95 - breath.value * 0.35,
    transform: [{ scale: 0.92 + breath.value * 0.14 }],
  }));

  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      // Margins leave room for the halo, which is wider than the mark — otherwise the
      // screen edge clips it.
      style={{ width: MARK, height: MARK, margin: (HALO - MARK) / 2 }}
    >
      <Animated.View
        style={[
          {
            position: 'absolute',
            left: (MARK - HALO) / 2,
            top: (MARK - HALO) / 2,
            width: HALO,
            height: HALO,
            borderRadius: radius.pill,
            backgroundColor: tint.coral,
          },
          halo,
        ]}
      />
      <LinearGradient
        colors={gradients.avatar}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[{ width: MARK, height: MARK, borderRadius: radius['3xl'] }, shadows.coral]}
      />
    </View>
  );
}
