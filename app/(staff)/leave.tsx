import React from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Chip, EmptyState, IconTile, ProgressBar, ScreenHeader } from '@/components/ui';
import { useLeaveBalances, useLeaveRequests } from '@/api/queries';
import { statusAccent } from '@/api/accents';
import type { LeaveBalance } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

const BALANCE_COLOR: Record<string, string> = {
  ANNUAL: palette.coral,
  MEDICAL: palette.sage,
  EMERGENCY: palette.amber,
};

function balanceColor(b: LeaveBalance): string {
  return BALANCE_COLOR[b.type.toUpperCase()] ?? palette.coral;
}

export default function Leave() {
  const router = useRouter();
  const balances = useLeaveBalances();
  const requests = useLeaveRequests();

  const pendingCount = requests.data?.filter((r) => r.status.toUpperCase() === 'PENDING').length ?? 0;
  const subtitle = requests.data
    ? pendingCount > 0
      ? `${pendingCount} pending approval`
      : 'Up to date'
    : undefined;

  return (
    <Screen>
      <ScreenHeader back title="Leave" subtitle={subtitle} />

      <AsyncBoundary
        loading={balances.isLoading || requests.isLoading}
        error={balances.error ?? requests.error}
        onRetry={() => {
          balances.refetch();
          requests.refetch();
        }}
      >
        {balances.data && requests.data && (
          <>
            {/* balances */}
            <View style={{ flexDirection: 'row', gap: 10 }}>
              {balances.data.map((b) => {
                const color = balanceColor(b);
                const used = b.entitled > 0 ? (b.entitled - b.remaining) / b.entitled : 0;
                return (
                  <Card key={b.type} padding={0} elevated={false} style={{ flex: 1, borderRadius: radius.xl, padding: 13, paddingTop: 14 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 3 }}>
                      <Text style={[font(800), { fontSize: 24, color }]}>{b.remaining}</Text>
                      <Text style={[font(600), { fontSize: 11, color: palette.faint, marginBottom: 3 }]}>/{b.entitled}</Text>
                    </View>
                    <Text style={[font(600), { fontSize: 11, color: palette.soft, marginTop: 8 }]}>{b.label}</Text>
                    <ProgressBar value={used} color={color} />
                  </Card>
                );
              })}
            </View>

            <Button label="Apply for leave" icon="plus" style={{ marginTop: 16 }} onPress={() => router.push('/leave-apply')} />

            <Text style={[font(700), { fontSize: 14, color: palette.ink, marginTop: 20, marginBottom: 12, marginHorizontal: 2 }]}>
              Your requests
            </Text>

            {requests.data.length === 0 ? (
              <EmptyState
                icon="sun"
                title="No leave requests yet"
                subtitle="When you apply for leave, your requests will show up here."
                tone={{ color: palette.coral, bg: tint.coral }}
                action={{ label: 'Apply for leave', icon: 'plus', onPress: () => router.push('/leave-apply') }}
              />
            ) : (
            <View style={{ gap: 10 }}>
              {requests.data.map((r) => {
                const s = statusAccent(r.status);
                return (
                  <Card key={r.id} padding={0} elevated={false} style={{ borderRadius: radius.xl, padding: 14, paddingHorizontal: 15, flexDirection: 'row', alignItems: 'center', gap: 13 }}>
                    <IconTile icon="sun" color={s.color} background={s.bg} size={40} iconSize={20} cornerRadius={radius.md} />
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]}>{r.typeLabel}</Text>
                      <Text style={[font(600), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]}>
                        {r.dateRangeLabel} · {r.durationLabel}
                      </Text>
                    </View>
                    <Chip label={r.statusLabel} color={s.color} background={s.bg} size="sm" />
                  </Card>
                );
              })}
            </View>
            )}
          </>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
