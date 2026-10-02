import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { palette, radius, font } from '@/theme';
import { Icon } from './Icon';

type Props = {
  /** The period being shown, e.g. "Jun 2026". */
  label: string;
  /** Step to the earlier period. Omit to disable (nothing earlier to show). */
  onPrev?: () => void;
  /** Step to the later period. Omit to disable (already at the latest). */
  onNext?: () => void;
};

/**
 * Compact "‹ Jun 2026 ›" period switcher for a header's trailing slot. A side
 * with nowhere to go is dimmed and inert — the control never pretends.
 */
export function MonthStepper({ label, onPrev, onNext }: Props) {
  const arrow = (dir: 'prev' | 'next', onPress?: () => void) => (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel={dir === 'prev' ? 'Previous month' : 'Next month'}
      accessibilityState={{ disabled: !onPress }}
      style={{ width: 30, height: 34, alignItems: 'center', justifyContent: 'center', opacity: onPress ? 1 : 0.3 }}
    >
      <Icon name={dir === 'prev' ? 'chevL' : 'chevR'} size={15} color={palette.soft} />
    </Pressable>
  );

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        borderRadius: radius.pill,
        backgroundColor: palette.surface,
        borderWidth: 1,
        borderColor: palette.line,
      }}
    >
      {arrow('prev', onPrev)}
      <Text
        accessibilityLiveRegion="polite"
        style={[font(700), { fontSize: 12, color: palette.ink, minWidth: 62, textAlign: 'center', fontVariant: ['tabular-nums'] }]}
        numberOfLines={1}
      >
        {label}
      </Text>
      {arrow('next', onNext)}
    </View>
  );
}
