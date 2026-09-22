import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { palette, radius, font } from '@/theme';
import { Icon } from './Icon';

type Props = {
  title: string;
  subtitle?: string;
  /** Show a back button on the left (pops the stack). */
  back?: boolean;
  /** Right-side element — a badge, avatar, or icon button. */
  badge?: React.ReactNode;
  /** Right-side text action (e.g. "Mark all read"). */
  action?: string;
  onAction?: () => void;
  /** Extra-large title (admin pages use 21/800; staff pages 20/700). */
  large?: boolean;
  marginBottom?: number;
};

/** Unified page header: optional back, title + subtitle, and a trailing slot. */
export function ScreenHeader({ title, subtitle, back, badge, action, onAction, large = false, marginBottom = 16 }: Props) {
  const router = useRouter();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom }}>
      {back && (
        <Pressable
          onPress={() => (router.canGoBack() ? router.back() : null)}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          style={{
            width: 40,
            height: 40,
            borderRadius: radius.md,
            backgroundColor: palette.surface,
            borderWidth: 1,
            borderColor: palette.line,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon name="chevL" size={18} color={palette.ink} />
        </Pressable>
      )}
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text
          style={[
            large ? font(800) : font(700),
            { fontSize: large ? 21 : 20, color: palette.ink, letterSpacing: -0.5 },
          ]}
          numberOfLines={1}
        >
          {title}
        </Text>
        {subtitle && (
          <Text style={[font(500), { fontSize: 12.5, color: palette.faint, marginTop: 6 }]} numberOfLines={1}>
            {subtitle}
          </Text>
        )}
      </View>
      {action ? (
        <Pressable onPress={onAction} accessibilityRole="button" accessibilityLabel={action}>
          <Text style={[font(600), { fontSize: 12.5, color: palette.coral }]}>{action}</Text>
        </Pressable>
      ) : (
        badge
      )}
    </View>
  );
}
