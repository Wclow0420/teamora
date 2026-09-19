import React from 'react';
import { View, Text, ViewStyle, StyleProp } from 'react-native';
import { font } from '@/theme';

type Props = {
  label: string;
  radius?: number;
  /** Diagonal stripe tint colour. */
  tintColor?: string;
  style?: StyleProp<ViewStyle>;
};

/**
 * Striped stand-in for real imagery (receipt thumbnails, maps, illustrations).
 * Uses a repeating diagonal of small bars to mimic the prototype's
 * repeating-linear-gradient placeholder without needing an SVG pattern.
 */
export function Placeholder({ label, radius = 16, tintColor = 'rgba(0,0,0,0.04)', style }: Props) {
  const bars = Array.from({ length: 36 });
  return (
    <View
      style={[
        {
          flex: 1,
          width: '100%',
          height: '100%',
          borderRadius: radius,
          overflow: 'hidden',
          backgroundColor: 'rgba(0,0,0,0.035)',
          alignItems: 'center',
          justifyContent: 'center',
        },
        style,
      ]}
    >
      <View style={{ position: 'absolute', inset: 0, flexDirection: 'row', transform: [{ rotate: '-35deg' }, { scale: 1.8 }] }}>
        {bars.map((_, i) => (
          <View key={i} style={{ width: 10, height: '200%', backgroundColor: i % 2 === 0 ? tintColor : 'transparent' }} />
        ))}
      </View>
      {!!label && (
        <Text style={[font(500), { fontSize: 10, letterSpacing: 0.6, color: 'rgba(0,0,0,0.55)', textTransform: 'uppercase' }]}>
          {label}
        </Text>
      )}
    </View>
  );
}
