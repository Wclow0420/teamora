import React from 'react';
import { Text, View } from 'react-native';
import { palette, tint, type } from '@/theme';
import { Card } from './Card';
import { IconTile } from './IconTile';
import type { IconName } from './Icon';

export type Point = {
  icon: IconName;
  text: string;
  /** Accent for the icon tile (defaults to neutral). */
  tone?: { color: string; bg: string };
};

type Props = { points: Point[] };

/**
 * A card of short explanatory points, each led by a tinted icon — for "what
 * happens next" / "what gets erased" explanations on account screens.
 */
export function PointList({ points }: Props) {
  return (
    <Card padding={0} style={{ paddingHorizontal: 16 }}>
      {points.map((p, i) => (
        <View
          key={p.text}
          style={{
            flexDirection: 'row',
            alignItems: 'flex-start',
            gap: 12,
            paddingVertical: 13,
            borderTopWidth: i ? 1 : 0,
            borderTopColor: palette.line,
          }}
        >
          <IconTile
            icon={p.icon}
            color={p.tone?.color ?? palette.soft}
            background={p.tone?.bg ?? tint.neutral}
            size={32}
            iconSize={16}
            cornerRadius={10}
          />
          <Text style={[type.body, { flex: 1, fontSize: 13.5, lineHeight: 20, color: palette.ink, marginTop: 5 }]}>
            {p.text}
          </Text>
        </View>
      ))}
    </Card>
  );
}
