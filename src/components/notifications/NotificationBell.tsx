import React from 'react';
import { View, Pressable } from 'react-native';
import { Icon } from '@/components/ui';
import { useNotifications } from '@/api/queries';
import { palette, radius, touchTarget } from '@/theme';

type Props = { onPress: () => void };

/**
 * Header bell that opens the notifications feed. The coral dot is the real
 * unread count from `useNotifications` — it only shows when something is unread.
 */
export function NotificationBell({ onPress }: Props) {
  const q = useNotifications();
  const unread = q.data?.unreadCount ?? 0;

  return (
    <Pressable
      onPress={onPress}
      hitSlop={6}
      accessibilityRole="button"
      accessibilityLabel={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
      style={({ pressed }) => ({
        width: touchTarget,
        height: touchTarget,
        borderRadius: radius.md,
        backgroundColor: palette.surface,
        borderWidth: 1,
        borderColor: palette.line,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: pressed ? 0.85 : 1,
      })}
    >
      <Icon name="bell" size={20} color={palette.ink} />
      {unread > 0 && (
        <View
          style={{
            position: 'absolute',
            top: 9,
            right: 10,
            width: 9,
            height: 9,
            borderRadius: radius.pill,
            backgroundColor: palette.coral,
            borderWidth: 1.5,
            borderColor: palette.surface,
          }}
        />
      )}
    </Pressable>
  );
}
