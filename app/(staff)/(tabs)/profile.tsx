import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Card, Chip, Icon, IconTile, StatTile, type IconName } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useAuth } from '@/hooks';
import { useMe, useLeaveBalances, usePayslips } from '@/api/queries';
import { palette, font, radius, tint } from '@/theme';

type MenuItem = { icon: IconName; label: string; meta?: string; color: string; bg: string };

/** Whole-number-ish years since an ISO join date, e.g. "2.3 yrs". */
function yearsSince(iso: string | null | undefined): string {
  if (!iso) return '—';
  const start = new Date(iso);
  if (Number.isNaN(start.getTime())) return '—';
  const years = (Date.now() - start.getTime()) / (1000 * 60 * 60 * 24 * 365.25);
  return `${years.toFixed(1)} yrs`;
}

export default function Profile() {
  const router = useRouter();
  const { signOut } = useAuth();
  const me = useMe();
  const balances = useLeaveBalances();
  const payslips = usePayslips();

  const leaveLeft = balances.data
    ? `${balances.data.reduce((sum, b) => sum + b.remaining, 0)} days`
    : '—';

  // Real payslip count (no fake placeholder) — blank until the query resolves.
  const payslipCount = payslips.data?.length ?? 0;
  const payslipsMeta = payslips.data ? `${payslipCount} available` : '';

  const menu: MenuItem[] = [
    { icon: 'user', label: 'Personal information', meta: '', color: palette.coral, bg: tint.coral },
    { icon: 'briefcase', label: 'Employment details', meta: me.data?.jobTitle ?? '', color: palette.sage, bg: tint.sage },
    // Documents feature has no backing data yet — no fake count.
    { icon: 'doc', label: 'Documents & contracts', meta: '', color: palette.amber, bg: tint.amber },
    { icon: 'wallet', label: 'Payslips', meta: payslipsMeta, color: palette.violet, bg: tint.violet },
    { icon: 'gear', label: 'App settings', meta: '', color: palette.soft, bg: tint.neutral },
    { icon: 'shield', label: 'Privacy & security', meta: '', color: palette.soft, bg: tint.neutral },
  ];

  const onLogout = () => {
    signOut();
    router.replace('/onboarding');
  };

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      title="Profile"
      accessory={
        <Pressable style={{ width: 40, height: 40, borderRadius: radius.md, backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.line, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="edit" size={18} color={palette.soft} />
        </Pressable>
      }
    >
      {/* identity + stats */}
      <AsyncBoundary loading={me.isLoading} error={me.error} onRetry={me.refetch}>
        {me.data && (
          <>
            <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 15, padding: 18, borderRadius: 22 }}>
              <Avatar initial={me.data.initial} size={62} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={[font(800), { fontSize: 18, color: palette.ink, letterSpacing: -0.4 }]}>{me.data.fullName}</Text>
                <Text style={[font(600), { fontSize: 12.5, color: palette.soft, marginTop: 6 }]}>{me.data.jobTitle ?? '—'}</Text>
                <Chip
                  label={me.data.department ?? '—'}
                  background={palette.bg}
                  color={palette.faint}
                  style={{ marginTop: 9 }}
                  leading={<Icon name="building" size={13} color={palette.faint} />}
                />
              </View>
            </Card>

            <View style={{ flexDirection: 'row', gap: 10, marginTop: 12 }}>
              <StatTile value={yearsSince(me.data.joinDate)} label="Tenure" valueSize={15} />
              <StatTile value={leaveLeft} label="Leave left" valueSize={15} />
              <StatTile value={me.data.staffId ?? '—'} label="Staff ID" valueSize={15} />
            </View>
          </>
        )}
      </AsyncBoundary>

      {/* menu */}
      <Card padding={0} style={{ marginTop: 14, paddingHorizontal: 16 }}>
        {menu.map((m, i) => (
          <View
            key={m.label}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 13, paddingVertical: 13, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}
          >
            <IconTile icon={m.icon} color={m.color} background={m.bg} size={36} iconSize={18} cornerRadius={11} />
            <Text style={[font(600), { flex: 1, fontSize: 13.5, color: palette.ink }]}>{m.label}</Text>
            {!!m.meta && <Text style={[font(600), { fontSize: 11.5, color: palette.faint }]}>{m.meta}</Text>}
            <Icon name="chevR" size={16} color={palette.faint} />
          </View>
        ))}
      </Card>

      {/* log out */}
      <Pressable onPress={onLogout} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, marginTop: 14 }}>
        <Icon name="logout" size={18} color={palette.danger} />
        <Text style={[font(700), { fontSize: 13.5, color: palette.danger }]}>Log out</Text>
      </Pressable>
    </CollapsingHeaderScreen>
  );
}
