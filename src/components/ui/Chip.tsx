import React from 'react';
import { View, Text, ViewStyle, StyleProp } from 'react-native';
import { palette, radius, font } from '@/theme';

type Props = {
  label: string;
  /** Text colour. */
  color?: string;
  /** Pill background. */
  background?: string;
  /** Render a leading status dot in `color`. */
  dot?: boolean;
  /** Leading element (e.g. a small Icon). */
  leading?: React.ReactNode;
  size?: 'sm' | 'md';
  style?: StyleProp<ViewStyle>;
};

/**
 * Rounded status pill — the workhorse badge for "Approved / Pending / Late /
 * Remote" states and meta tags. Pair `background` with a tint and `color` with
 * the matching accent (see theme.accents).
 */
export function Chip({
  label,
  color = palette.soft,
  background = palette.surface,
  dot = false,
  leading,
  size = 'md',
  style,
}: Props) {
  const sm = size === 'sm';
  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          gap: 5,
          alignSelf: 'flex-start',
          paddingVertical: sm ? 4 : 5,
          paddingHorizontal: sm ? 9 : 11,
          borderRadius: radius.pill,
          backgroundColor: background,
        },
        style,
      ]}
    >
      {dot && <View style={{ width: 6, height: 6, borderRadius: radius.pill, backgroundColor: color }} />}
      {leading}
      <Text style={[font(700), { fontSize: sm ? 10.5 : 11.5, color }]} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}
