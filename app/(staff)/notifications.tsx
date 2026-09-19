import React from 'react';
import { View, Text } from 'react-native';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Card, EmptyState, IconTile, SectionLabel, ScreenHeader, type IconName } from '@/components/ui';
import { useNotifications, useMarkAllRead } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import type { NotificationItem } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

function NotifItem({ item, first }: { item: NotificationItem; first: boolean }) {
  const accent = accentFromKey(item.accentColorKey);
  return (
    <View style={{ flexDirection: 'row', gap: 12, paddingVertical: 13, borderTopWidth: first ? 0 : 1, borderTopColor: palette.line }}>
      <IconTile icon={item.iconName as IconName} color={accent.color} background={accent.bg} size={42} iconSize={20} cornerRadius={radius.md} />
      <View style={{ flex: 1, minWidth: 0 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7 }}>
          <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]}>{item.title}</Text>
          {item.unread && <View style={{ width: 7, height: 7, borderRadius: radius.pill, backgroundColor: palette.coral }} />}
        </View>
        {item.body && <Text style={[font(500), { fontSize: 12, lineHeight: 17, color: palette.soft, marginTop: 6 }]}>{item.body}</Text>}
        <Text style={[font(600), { fontSize: 11, color: palette.faint, marginTop: 7 }]}>{item.timeLabel}</Text>
      </View>
    </View>
  );
}

export default function Notifications() {
  const q = useNotifications();
  const markAll = useMarkAllRead();

  return (
    <Screen>
      <ScreenHeader
        back
        title="Notifications"
        action={q.data && q.data.unreadCount > 0 ? 'Mark all read' : undefined}
        onAction={() => markAll.mutate()}
      />

      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && (
          <>
            {q.data.today.length > 0 && (
              <>
                <SectionLabel title="Today" eyebrow />
                <Card padding={0} style={{ paddingHorizontal: 16 }}>
                  {q.data.today.map((n, i) => (
                    <NotifItem key={n.id} item={n} first={i === 0} />
                  ))}
                </Card>
              </>
            )}

            {q.data.earlier.length > 0 && (
              <View style={{ marginTop: q.data.today.length > 0 ? 20 : 0 }}>
                <SectionLabel title="Earlier" eyebrow />
                <Card padding={0} style={{ paddingHorizontal: 16 }}>
                  {q.data.earlier.map((n, i) => (
                    <NotifItem key={n.id} item={n} first={i === 0} />
                  ))}
                </Card>
              </View>
            )}

            {q.data.today.length === 0 && q.data.earlier.length === 0 && (
              <EmptyState
                icon="bell"
                title="You're all caught up"
                subtitle="No notifications yet — we'll let you know when something needs you."
                tone={{ color: palette.violet, bg: tint.violet }}
              />
            )}
          </>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
