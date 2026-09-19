import React from 'react';
import { View, Text } from 'react-native';
import { palette, font, type as typePresets } from '@/theme';

type Props = {
  title: string;
  /** Optional right-aligned action / meta text. */
  action?: string;
  actionColor?: string;
  /** Uppercase eyebrow style (Today / Earlier section dividers). */
  eyebrow?: boolean;
};

/** "Recent activity / See all" style row that titles a section of cards. */
export function SectionLabel({ title, action, actionColor = palette.coral, eyebrow = false }: Props) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: eyebrow ? 8 : 11 }}>
      <Text style={eyebrow ? typePresets.eyebrow : [font(700), { fontSize: 14, color: palette.ink }]}>
        {eyebrow ? title.toUpperCase() : title}
      </Text>
      {action && <Text style={[font(600), { fontSize: 12.5, color: actionColor }]}>{action}</Text>}
    </View>
  );
}
