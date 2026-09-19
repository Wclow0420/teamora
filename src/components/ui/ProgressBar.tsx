import React from 'react';
import { View } from 'react-native';
import { palette, radius } from '@/theme';

type Props = {
  /** 0–1 fill ratio. */
  value: number;
  color: string;
  track?: string;
  height?: number;
};

/** Thin rounded progress track — leave balances, etc. */
export function ProgressBar({ value, color, track = palette.line, height = 5 }: Props) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  return (
    <View style={{ height, borderRadius: radius.pill, backgroundColor: track, overflow: 'hidden' }}>
      <View style={{ width: `${pct}%`, height: '100%', borderRadius: radius.pill, backgroundColor: color }} />
    </View>
  );
}
