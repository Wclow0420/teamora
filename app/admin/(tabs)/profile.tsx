import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Card, Chip, Icon, IconTile, StatTile, type IconName } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useAuth } from '@/hooks';
import { useMe } from '@/api/queries';
import type { Role } from '@/api/types';
import { palette, font, radius, gradients, tint } from '@/theme';

type MenuItem = { icon: IconName; label: string; meta?: string; color: string; bg: string; href?: string };

const ROLE_LABEL: Record<Role, string> = {
  OWNER: 'Owner',
  HR_ADMIN: 'HR Admin',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

export default function AdminProfile() {
  const router = useRouter();
  const { signOut } = useAuth();
  const me = useMe();

  const menu: MenuItem[] = [
    { icon: 'building', label: 'Company settings', meta: '', color: palette.coral, bg: tint.coral, href: '/admin/company-settings' },
    { icon: 'pin', label: 'Work locations', meta: '', color: palette.violet, bg: tint.violet, href: '/admin/work-locations' },
    { icon: 'calendar', label: 'Company calendar', meta: '', color: palette.amber, bg: tint.amber, href: '/admin/calendar-events' },
    { icon: 'users', label: 'Team members', meta: '', color: palette.sage, bg: tint.sage, href: '/admin/staff' },
    { icon: 'user', label: 'Personal information', meta: '', color: palette.amber, bg: tint.amber },
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
      <AsyncBoundary loading={me.isLoading} error={me.error} onRetry={me.refetch}>
        {me.data && (
          <>
            {/* identity */}
            <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 15, padding: 18, borderRadius: 22 }}>
              <Avatar initial={me.data.initial} size={62} colors={gradients.violet} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={[font(800), { fontSize: 18, color: palette.ink, letterSpacing: -0.4 }]}>{me.data.fullName}</Text>
                <Text style={[font(600), { fontSize: 12.5, color: palette.soft, marginTop: 6 }]}>{me.data.jobTitle ?? ROLE_LABEL[me.data.role]}</Text>
                <Chip
                  label={me.data.companyName ?? '—'}
                  background={palette.bg}
                  color={palette.faint}
                  style={{ marginTop: 9 }}
                  leading={<Icon name="building" size={13} color={palette.faint} />}
                />
              </View>
            </Card>

            {/* stats */}
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 12 }}>
              <StatTile value={ROLE_LABEL[me.data.role]} label="Role" valueSize={15} color={palette.violet} />
              <StatTile value={me.data.department ?? '—'} label="Department" valueSize={15} />
              <StatTile value={me.data.staffId ?? '—'} label="Staff ID" valueSize={15} />
            </View>
          </>
        )}
      </AsyncBoundary>

      {/* menu */}
      <Card padding={0} style={{ marginTop: 14, paddingHorizontal: 16 }}>
        {menu.map((m, i) => (
          <Pressable
            key={m.label}
            disabled={!m.href}
            onPress={() => m.href && router.push(m.href as never)}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 13, paddingVertical: 13, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}
          >
            <IconTile icon={m.icon} color={m.color} background={m.bg} size={36} iconSize={18} cornerRadius={11} />
            <Text style={[font(600), { flex: 1, fontSize: 13.5, color: palette.ink }]}>{m.label}</Text>
            <Icon name="chevR" size={16} color={palette.faint} />
          </Pressable>
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
