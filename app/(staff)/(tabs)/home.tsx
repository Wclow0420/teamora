import React from 'react';
import { View, Text, type TextStyle } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Button, Card, Icon, IconTile, LiveDot } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useLiveTimer, pad2 } from '@/hooks';
import { useMe, useTodayAttendance, useClockOut, useLeaveBalances, useClaims } from '@/api/queries';
import type { IconName } from '@/components/ui';
import type { TodayStatus } from '@/api/types';
import { palette, font, radius, tint, shadows } from '@/theme';

const NUM: TextStyle = { fontVariant: ['tabular-nums'] };

const QUICK = [
  { icon: 'leave', label: 'Leave', bg: tint.coral, color: palette.coral, href: '/leave' },
  { icon: 'receipt', label: 'Claim', bg: tint.amber, color: palette.amber, href: '/claims' },
  { icon: 'calendar', label: 'Schedule', bg: tint.sage, color: palette.sage, href: '/calendar' },
  { icon: 'wallet', label: 'Payroll', bg: tint.violet, color: palette.violet, href: '/payroll' },
] as const;

/** Today's attendance status → a short, human word for the summary tile. */
function attendanceWord(status: string | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'PRESENT':
    case 'WORKING':
      return 'On time';
    case 'REMOTE':
      return 'Remote';
    case 'LATE':
      return 'Late';
    case 'ON_LEAVE':
      return 'On leave';
    case 'ABSENT':
      return 'Not in';
    default:
      return '—';
  }
}

/** Time-aware greeting. */
function greeting(d: Date): string {
  const h = d.getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

/** Format an ISO instant to "h:mm AM/PM". Returns "—" when null. */
function formatClockIn(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  let h = d.getHours();
  const m = d.getMinutes();
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12;
  if (h === 0) h = 12;
  return `${h}:${pad2(m)} ${ampm}`;
}

/** Espresso worked-time hero; mounts after today data resolves so the timer base is correct. */
function AttendanceHero({ today, onClockOut }: { today: TodayStatus; onClockOut: () => void }) {
  const { h, m, s } = useLiveTimer((today.workedMinutes ?? 0) * 60);
  return (
    <View style={[{ backgroundColor: palette.espresso, borderRadius: radius.hero, padding: 20, paddingBottom: 18, overflow: 'hidden' }, shadows.float]}>
      <View style={{ position: 'absolute', right: -84, top: -104, width: 230, height: 230, borderRadius: 999, backgroundColor: 'rgba(236,106,77,0.10)' }} />

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <LiveDot color="#7FB894" />
          <Text style={[font(700), { fontSize: 11, letterSpacing: 1.1, textTransform: 'uppercase', color: 'rgba(255,255,255,0.55)' }]}>Currently working</Text>
        </View>
        <Text style={[font(500), { fontSize: 12, color: 'rgba(255,255,255,0.6)' }]}>In at {formatClockIn(today.clockInAt)}</Text>
      </View>

      <Text style={[font(800), { fontSize: 44, lineHeight: 46, letterSpacing: -1.4, color: palette.white, marginTop: 13 }, NUM]}>
        {h}:{pad2(m)}
        <Text style={[font(800), { fontSize: 24, color: 'rgba(255,255,255,0.5)', letterSpacing: -0.5 }]}>:{pad2(s)}</Text>
      </Text>

      <Text style={[font(500), { fontSize: 12.5, color: 'rgba(255,255,255,0.6)', marginTop: 6 }]}>
        Worked today{today.shift ? ` · ${today.shift}` : ''}
      </Text>

      <Button label="Clock Out" icon="clock" variant="light" height={48} style={{ marginTop: 16 }} onPress={onClockOut} />
    </View>
  );
}

export default function Home() {
  const router = useRouter();
  const me = useMe();
  const today = useTodayAttendance();
  const clockOut = useClockOut();
  const balances = useLeaveBalances();
  const claims = useClaims();

  const firstName = me.data?.fullName.split(' ')[0];
  const title = `${firstName ?? '·'} 👋`;
  const initial = me.data?.initial ?? '·';

  // Today's summary tiles — all real: today's status, annual-leave balance, pending claims.
  const annual = balances.data?.find((b) => /annual/i.test(b.type) || /annual/i.test(b.label)) ?? balances.data?.[0];
  const summary: { icon: IconName; value: string; label: string; color: string; num?: boolean }[] = [
    { icon: 'briefcase', value: attendanceWord(today.data?.status), label: 'Attendance', color: palette.sage },
    { icon: 'leave', value: annual ? `${annual.remaining}d` : '—', label: annual?.label ?? 'Annual leave', color: palette.coral, num: true },
    { icon: 'receipt', value: claims.data?.pendingTotalLabel ?? '—', label: 'Claims pending', color: palette.amber, num: true },
  ];

  const onClockOut = () => {
    // Fire the mutation (auto-refreshes) and continue to the clock-in screen.
    clockOut.mutateAsync().catch(() => {});
    router.push('/clock-in');
  };

  return (
    <CollapsingHeaderScreen
      collapsible
      bottomInset={70}
      title={title}
      titleSize={25}
      eyebrow={
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
          <Icon name="sun" size={15} color={palette.amber} />
          <Text style={[font(500), { fontSize: 13, color: palette.soft }]}>{greeting(new Date())}</Text>
        </View>
      }
      accessory={<Avatar initial={initial} size={46} online />}
    >
      {/* attendance hero */}
      <AsyncBoundary loading={today.isLoading} error={today.error} onRetry={today.refetch}>
        {today.data && <AttendanceHero today={today.data} onClockOut={onClockOut} />}
      </AsyncBoundary>

      {/* manager approvals inbox */}
      {me.data?.role === 'MANAGER' && (
        <Card padding={14} onPress={() => router.push('/approvals')} style={{ marginTop: 16, flexDirection: 'row', alignItems: 'center', gap: 13 }}>
          <IconTile icon="check" color={palette.sage} background={tint.sage} size={42} cornerRadius={13} />
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={[font(700), { fontSize: 14, color: palette.ink }]}>Approvals</Text>
            <Text style={[font(500), { fontSize: 12, color: palette.faint, marginTop: 3 }]}>Review your team's requests</Text>
          </View>
          <Icon name="chevR" size={18} color={palette.faint} />
        </Card>
      )}

      {/* quick actions */}
      <View style={{ flexDirection: 'row', gap: 11, marginTop: 18 }}>
        {QUICK.map((q) => (
          <View key={q.label} style={{ flex: 1, alignItems: 'center', gap: 8 }}>
            <Card padding={0} elevated={false} onPress={() => router.push(q.href as never)} style={{ width: '100%', aspectRatio: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: q.bg, borderColor: 'transparent', borderRadius: radius.xl }}>
              <Icon name={q.icon} size={24} color={q.color} />
            </Card>
            <Text style={[font(600), { fontSize: 11.5, color: palette.soft }]}>{q.label}</Text>
          </View>
        ))}
      </View>

      {/* today summary */}
      <Card padding={16} style={{ marginTop: 18, borderRadius: radius['3xl'] }}>
        <Text style={[font(700), { fontSize: 14, letterSpacing: -0.2, color: palette.ink, marginBottom: 13 }]}>Today's summary</Text>
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {summary.map((it) => (
            <View key={it.label} style={{ flex: 1, backgroundColor: palette.surfaceSunken, borderRadius: radius.lg, padding: 13 }}>
              <Icon name={it.icon} size={18} color={it.color} />
              <Text numberOfLines={1} style={[font(800), { fontSize: 17, letterSpacing: -0.3, color: palette.ink, marginTop: 10 }, it.num ? NUM : null]}>{it.value}</Text>
              <Text numberOfLines={1} style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]}>{it.label}</Text>
            </View>
          ))}
        </View>
      </Card>
    </CollapsingHeaderScreen>
  );
}
