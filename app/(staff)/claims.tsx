import React from 'react';
import { View, Text } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Chip, EmptyState, Icon, Placeholder, ScreenHeader } from '@/components/ui';
import { useClaims } from '@/api/queries';
import { statusAccent } from '@/api/accents';
import { palette, font, radius, gradients, tint } from '@/theme';

export default function Claims() {
  const router = useRouter();
  const claims = useClaims();
  const summary = claims.data;

  return (
    <Screen>
      <ScreenHeader
        back
        title="Claims"
        subtitle={summary ? `RM ${summary.reimbursedThisMonthLabel} reimbursed this month` : 'Claims'}
      />

      <AsyncBoundary loading={claims.isLoading} error={claims.error} onRetry={claims.refetch}>
        {summary && (
          <>
            {/* pending highlight */}
            <LinearGradient
              colors={gradients.cream}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={{
                borderRadius: 22,
                padding: 18,
                borderWidth: 1,
                borderColor: '#F1D2B4',
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <View>
                <Text style={[font(600), { fontSize: 12, color: palette.soft, letterSpacing: 0.4 }]}>PENDING APPROVAL</Text>
                <Text style={[font(800), { fontSize: 30, color: palette.ink, marginTop: 9 }]}>RM {summary.pendingTotalLabel}</Text>
              </View>
              <View
                style={{
                  width: 52,
                  height: 52,
                  borderRadius: radius.lg,
                  backgroundColor: palette.white,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Icon name="clock" size={24} color={palette.amber} />
              </View>
            </LinearGradient>

            <Button label="Add a claim" icon="camera" variant="dark" style={{ marginTop: 14 }} onPress={() => router.push('/claim-submit')} />

            <Text style={[font(700), { fontSize: 14, color: palette.ink, marginTop: 20, marginBottom: 12, marginHorizontal: 2 }]}>
              Recent claims
            </Text>

            {summary.claims.length === 0 ? (
              <EmptyState
                icon="receipt"
                title="No claims yet"
                subtitle="Submit an expense claim and it'll show up here for tracking."
                tone={{ color: palette.coral, bg: tint.coral }}
                action={{ label: 'Add a claim', icon: 'camera', onPress: () => router.push('/claim-submit') }}
              />
            ) : (
            <View style={{ gap: 10 }}>
              {summary.claims.map((c) => {
                const s = statusAccent(c.status);
                return (
                  <Card key={c.id} padding={0} elevated={false} style={{ borderRadius: radius.xl, padding: 12, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                    <View style={{ width: 46, height: 46, borderRadius: radius.md, overflow: 'hidden' }}>
                      <Placeholder label="rcpt" radius={radius.md} />
                    </View>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>{c.title}</Text>
                      <Text style={[font(600), { fontSize: 11, color: palette.faint, marginTop: 6 }]}>{c.claimDateLabel}</Text>
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text style={[font(800), { fontSize: 14, color: palette.ink }]}>RM {c.amountLabel}</Text>
                      <Chip label={c.statusLabel} color={s.color} background={s.bg} size="sm" style={{ marginTop: 7 }} />
                    </View>
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
