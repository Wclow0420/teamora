import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Card, Chip, Icon, IconTile, StatTile, type IconName } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useAuth } from '@/hooks';
import { useMe, useLeaveBalances, usePayslips } from '@/api/queries';
import { formatDays } from '@/lib/numbers';
import { palette, font, tint } from '@/theme';

type MenuItem = {
  icon: IconName;
  label: string;
  meta?: string;
  color: string;
  bg: string;
  href: '/payroll' | '/leave' | '/claims' | '/notifications' | '/my-details' | '/change-password' | '/privacy';
};

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

  // Annual leave is the number people mean by "leave left" — summing medical,
  // emergency etc. into one figure (e.g. "35 days") overstates what they can take.
  const annual = balances.data?.find((b) => b.code === 'ANNUAL') ?? balances.data?.find((b) => b.accrual !== 'NONE');
  const leaveLeft = balances.data ? (annual ? formatDays(annual.remaining) : '—') : '—';

  // Real payslip count (no fake placeholder) — blank until the query resolves.
  const payslipCount = payslips.data?.length ?? 0;
  const payslipsMeta = payslips.data ? `${payslipCount} available` : '';

  // Every row goes somewhere real — a chevron never promises a screen that doesn't exist.
  const menu: MenuItem[] = [
    { icon: 'wallet', label: 'Payslips', meta: payslipsMeta, color: palette.violet, bg: tint.violet, href: '/payroll' },
    { icon: 'leave', label: 'Leave', color: palette.coral, bg: tint.coral, href: '/leave' },
    { icon: 'receipt', label: 'Claims', color: palette.amber, bg: tint.amber, href: '/claims' },
    { icon: 'bell', label: 'Notifications', color: palette.sage, bg: tint.sage, href: '/notifications' },
  ];

  // Account — what HR has on file for me, and my sign-in password.
  const account: MenuItem[] = [
    { icon: 'user', label: 'My details', color: palette.coral, bg: tint.coral, href: '/my-details' },
    { icon: 'lock', label: 'Change password', color: palette.soft, bg: tint.neutral, href: '/change-password' },
    { icon: 'shield', label: 'Privacy & terms', color: palette.violet, bg: tint.violet, href: '/privacy' },
  ];

  const onLogout = () => {
    signOut();
    router.replace('/onboarding');
  };

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      title="Profile"
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
              <StatTile value={leaveLeft} label={annual?.code === 'ANNUAL' ? 'Annual left' : 'Leave left'} valueSize={15} />
              <StatTile value={me.data.staffId ?? '—'} label="Staff ID" valueSize={15} />
            </View>
          </>
        )}
      </AsyncBoundary>

      {/* menus */}
      {[account, menu].map((group, g) => (
        <Card key={g} padding={0} style={{ marginTop: 14, paddingHorizontal: 16 }}>
          {group.map((m, i) => (
            <Pressable
              key={m.label}
              accessibilityRole="button"
              onPress={() => router.push(m.href)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 13, paddingVertical: 13, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}
            >
              <IconTile icon={m.icon} color={m.color} background={m.bg} size={36} iconSize={18} cornerRadius={11} />
              <Text style={[font(600), { flex: 1, fontSize: 13.5, color: palette.ink }]}>{m.label}</Text>
              {!!m.meta && <Text style={[font(600), { fontSize: 11.5, color: palette.faint }]}>{m.meta}</Text>}
              <Icon name="chevR" size={16} color={palette.faint} />
            </Pressable>
          ))}
        </Card>
      ))}

      {/* log out */}
      <Pressable onPress={onLogout} accessibilityRole="button" accessibilityLabel="Log out" style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, marginTop: 14 }}>
        <Icon name="logout" size={18} color={palette.danger} />
        <Text style={[font(700), { fontSize: 13.5, color: palette.danger }]}>Log out</Text>
      </Pressable>

      {/* account deletion (App Store 5.1.1(v)) — deliberately quiet */}
      <Pressable
        onPress={() => router.push('/delete-account')}
        accessibilityRole="button"
        hitSlop={8}
        style={{ alignSelf: 'center', marginTop: 22, paddingVertical: 4 }}
      >
        <Text style={[font(600), { fontSize: 12, color: palette.faint }]}>Delete my account</Text>
      </Pressable>
    </CollapsingHeaderScreen>
  );
}
