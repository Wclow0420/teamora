import React from 'react';
import { View, Text } from 'react-native';
import { Card } from './Card';
import { palette, font } from '@/theme';

type Props = {
  value: string;
  label: string;
  /** Value colour (defaults to ink). */
  color?: string;
  valueSize?: number;
};

/** Compact centred KPI tile — the 3/4-up stat rows on attendance, profile, live. */
export function StatTile({ value, label, color = palette.ink, valueSize = 19 }: Props) {
  return (
    <Card padding={12} elevated={false} style={{ flex: 1, alignItems: 'center' }}>
      <Text style={[font(800), { fontSize: valueSize, color }]} numberOfLines={1}>
        {value}
      </Text>
      <Text style={[font(600), { fontSize: 10.5, color: palette.faint, marginTop: 6 }]} numberOfLines={1}>
        {label}
      </Text>
    </Card>
  );
}
