import React from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, BarChart, Card, Chip, Icon, IconTile, SectionLabel } from '@/components/ui';
import type { IconName } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useDashboard, useMe } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import type { DashboardSummary, Role } from '@/api/types';
import { palette, font, radius, gradients, tint } from '@/theme';

const ROLE_LABEL: Record<Role, string> = {
  OWNER: 'Owner',
  HR_ADMIN: 'HR Admin',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Time-aware greeting so the header reflects when the manager actually opens it. */
function greeting(d: Date): string {
  const h = d.getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

/** "Mon, 15 Jun" — deterministic, no Intl/locale dependency. */
function todayLabel(d: Date): string {
  return `${DAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

/** type → list icon (status is conveyed by the accent colour). */
const ACTIVITY_ICON: Record<DashboardSummary['activity'][number]['type'], IconName> = {
  leave: 'leave',
  claim: 'receipt',
  overtime: 'clock',
};

const QUICK: { icon: IconName; label: string; href: '/admin/live' | '/admin/schedule'; color: string; bg: string }[] = [
  { icon: 'pin', label: 'Live attendance', href: '/admin/live', color: palette.sage, bg: tint.sage },
  { icon: 'schedule', label: 'Week schedule', href: '/admin/schedule', color: palette.violet, bg: tint.violet },
];

export default function Dashboard() {
  const router = useRouter();
  const me = useMe();
  const dash = useDashboard();

  const now = new Date();
  const firstName = me.data?.fullName.split(' ')[0] ?? '·';
  const roleLabel = me.data ? ROLE_LABEL[me.data.role] : '';
  const subtitle = `${todayLabel(now)}${roleLabel ? ` · ${roleLabel}` : ''}`;
  const todayIdx = (now.getDay() + 6) % 7; // Mon=0 .. Sun=6

  const d = dash.data;
  const kpis: { icon: IconName; value: string; label: string; color: string; bg: string }[] = [
    { icon: 'users', value: d ? String(d.presentToday) : '—', label: d ? `/${d.headcount} present` : 'present', color: palette.sage, bg: tint.sage },
    { icon: 'sun', value: d ? String(d.onLeaveToday) : '—', label: 'on leave', color: palette.coral, bg: tint.coral },
    { icon: 'check', value: d ? String(d.pendingApprovals) : '—', label: 'approvals', color: palette.amber, bg: tint.amber },
    { icon: 'wallet', value: d ? `RM ${d.payrollDueLabel}` : '—', label: 'payroll due', color: palette.violet, bg: tint.violet },
  ];

  return (
    <CollapsingHeaderScreen
      collapsible
      large
      bottomInset={70}
      title={`${greeting(now)}, ${firstName}`}
      subtitle={subtitle}
      accessory={<Avatar initial={me.data?.initial ?? '·'} colors={gradients.violet} size={44} />}
      onAccessoryPress={() => router.push('/admin/profile')}
    >
      <AsyncBoundary loading={dash.isLoading} error={dash.error} onRetry={dash.refetch}>
        {d && (
          <>
            {/* KPI 2x2 grid */}
            <View style={{ gap: 11 }}>
              {[kpis.slice(0, 2), kpis.slice(2, 4)].map((row, ri) => (
                <View key={ri} style={{ flexDirection: 'row', gap: 11 }}>
                  {row.map((k) => (
                    <Card key={k.label} padding={15} style={{ flex: 1, borderRadius: radius.xl }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                        <IconTile icon={k.icon} color={k.color} background={k.bg} size={36} iconSize={19} cornerRadius={11} />
                      </View>
                      <Text style={[font(800), { fontSize: 23, color: palette.ink, marginTop: 13, letterSpacing: -0.4 }]}>{k.value}</Text>
                      <Text style={[font(600), { fontSize: 11.5, color: palette.faint, marginTop: 6 }]}>{k.label}</Text>
                    </Card>
                  ))}
                </View>
              ))}
            </View>

            {/* week chart */}
            <Card padding={16} style={{ marginTop: 14, borderRadius: radius['2xl'] }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]}>Attendance this week</Text>
                <Chip label={d.week.rateLabel} color={palette.sage} background={tint.sage} leading={<Icon name="trend" size={14} color={palette.sage} />} />
              </View>
              <BarChart
                data={d.week.present}
                max={Math.max(d.week.max, 1)}
                labels={d.week.labels}
                highlight={[todayIdx]}
                barColor="#EAD2BE"
              />
            </Card>

            {/* recent activity */}
            <View style={{ marginTop: 20 }}>
              <SectionLabel title="Recent activity" />
              <Card padding={0}>
                {d.activity.length === 0 ? (
                  <View style={{ paddingVertical: 22, alignItems: 'center' }}>
                    <Text style={[font(500), { fontSize: 12.5, color: palette.faint }]}>No activity yet</Text>
                  </View>
                ) : (
                  d.activity.map((f, i) => {
                    const accent = accentFromKey(f.accent);
                    return (
                      <View
                        key={i}
                        style={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 12,
                          paddingVertical: 12,
                          paddingHorizontal: 16,
                          borderTopWidth: i ? 1 : 0,
                          borderTopColor: palette.line,
                        }}
                      >
                        <IconTile icon={ACTIVITY_ICON[f.type]} color={accent.color} background={accent.bg} size={34} iconSize={17} cornerRadius={10} />
                        <Text style={[font(600), { flex: 1, fontSize: 13, color: palette.ink, lineHeight: 17 }]}>{f.text}</Text>
                        <Text style={[font(600), { fontSize: 11, color: palette.faint }]}>{f.timeLabel}</Text>
                      </View>
                    );
                  })
                )}
              </Card>
            </View>
          </>
        )}
      </AsyncBoundary>

      {/* quick access — Live + Schedule (not in the tab bar) */}
      <View style={{ marginTop: 20 }}>
        <SectionLabel title="Quick access" />
        <View style={{ flexDirection: 'row', gap: 11 }}>
          {QUICK.map((q) => (
            <Card key={q.href} padding={14} onPress={() => router.push(q.href)} style={{ flex: 1, borderRadius: radius.xl }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <IconTile icon={q.icon} color={q.color} background={q.bg} size={36} iconSize={19} cornerRadius={11} />
                <Icon name="chevR" size={15} color={palette.faint} />
              </View>
              <Text style={[font(700), { fontSize: 13, color: palette.ink, marginTop: 12 }]}>{q.label}</Text>
            </Card>
          ))}
        </View>
      </View>
    </CollapsingHeaderScreen>
  );
}
