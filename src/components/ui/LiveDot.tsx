import React, { useEffect } from 'react';
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming, Easing } from 'react-native-reanimated';
import { radius } from '@/theme';

type Props = {
  color: string;
  size?: number;
};

/** Softly blinking status dot (the "LIVE" / "Scanning…" indicator). */
export function LiveDot({ color, size = 7 }: Props) {
  const opacity = useSharedValue(1);
  useEffect(() => {
    opacity.value = withRepeat(withTiming(0.2, { duration: 650, easing: Easing.inOut(Easing.quad) }), -1, true);
  }, [opacity]);
  const style = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return <Animated.View style={[{ width: size, height: size, borderRadius: radius.pill, backgroundColor: color }, style]} />;
}
