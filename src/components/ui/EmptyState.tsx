import React from 'react';
import { View, Text } from 'react-native';
import { Card } from './Card';
import { Icon, type IconName } from './Icon';
import { Button } from './Button';
import { palette, font, radius, tint } from '@/theme';

export type EmptyStateProps = {
  icon: IconName;
  title: string;
  subtitle?: string;
  /** Accent for the icon tile (defaults to a neutral tone). */
  tone?: { color: string; bg: string };
  /** Optional call-to-action button. */
  action?: { label: string; icon?: IconName; onPress: () => void };
};

/**
 * A friendly, honest empty state for lists/sections with no data yet. Centered
 * tinted icon + title + optional subtitle + optional CTA. Use inside an
 * AsyncBoundary's success branch when a collection is empty.
 */
export function EmptyState({ icon, title, subtitle, tone, action }: EmptyStateProps) {
  const color = tone?.color ?? palette.soft;
  const bg = tone?.bg ?? tint.neutral;
  return (
    <Card style={{ alignItems: 'center', paddingVertical: 30, paddingHorizontal: 22 }}>
      <View
        style={{
          width: 56,
          height: 56,
          borderRadius: radius.xl,
          backgroundColor: bg,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 14,
        }}
      >
        <Icon name={icon} size={26} color={color} />
      </View>
      <Text style={[font(700), { fontSize: 15, color: palette.ink, textAlign: 'center' }]}>{title}</Text>
      {subtitle ? (
        <Text style={[font(500), { fontSize: 12.5, color: palette.faint, textAlign: 'center', marginTop: 6, lineHeight: 18 }]}>
          {subtitle}
        </Text>
      ) : null}
      {action ? (
        <Button label={action.label} icon={action.icon} onPress={action.onPress} style={{ marginTop: 18, alignSelf: 'stretch' }} />
      ) : null}
    </Card>
  );
}
