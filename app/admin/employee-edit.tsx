import React from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Avatar, Card, Chip, Icon, IconTile, ScreenHeader, type IconName } from '@/components/ui';
import { useAdminLeaveBalances, useEmployee } from '@/api/queries';
import type { EmployeeResponse, LeaveBalance, Role } from '@/api/types';
import { ringgit, summaryLine } from '@/lib/employeeFields';
import { formatDecimal } from '@/lib/numbers';
import { describeMask } from '@/lib/workweek';
import { palette, font, radius, spacing, tint } from '@/theme';

const ROLE_LABEL: Record<Role, string> = {
  OWNER: 'Owner',
  HR_ADMIN: 'HR Admin',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

const ROLE_TONE: Record<Role, { color: string; bg: string }> = {
  OWNER: { color: palette.violet, bg: tint.violet },
  HR_ADMIN: { color: palette.coral, bg: tint.coral },
  MANAGER: { color: palette.amber, bg: tint.amber },
  EMPLOYEE: { color: palette.soft, bg: tint.neutral },
};

/** A category tile: one slice of the employee record, edited on its own screen. */
type Category = {
  key: string;
  title: string;
  icon: IconName;
  color: string;
  background: string;
  /** One-line recap of the current values; `null` renders a muted "Not set". */
  summary: string | null;
  /** True while this tile's own data is still loading — keeps the line blank. */
  pending?: boolean;
  href: Href;
};

/** "Sales Executive · Retail" */
function profileSummary(e: EmployeeResponse): string | null {
  return summaryLine([e.jobTitle, e.department]);
}

/** "Employee · Nadia Rahman" */
function employmentSummary(e: EmployeeResponse): string | null {
  return summaryLine([ROLE_LABEL[e.role], e.reportingManagerName ?? 'Owner approves']);
}

/** "RM 4,000 · Mon–Fri · 8h" */
function compensationSummary(e: EmployeeResponse): string | null {
  return summaryLine([
    e.monthlySalary != null ? ringgit(e.monthlySalary) : 'No salary',
    describeMask(e.workingDays),
    `${formatDecimal(e.hoursPerDay)}h`,
  ]);
}

/** "NRIC set · Maybank" */
function statutorySummary(e: EmployeeResponse): string | null {
  return summaryLine([
    e.nric ? 'NRIC set' : null,
    e.bankName ?? (e.bankAccountNo ? 'Bank set' : null),
    e.epfNo ? 'EPF no.' : null,
  ]);
}

/** "Annual 16 · Medical 14" — the first two leave types, with their entitlement. */
function leaveSummary(rows: LeaveBalance[]): string | null {
  return summaryLine(rows.slice(0, 2).map((b) => `${b.name} ${formatDecimal(b.entitled)}`));
}

/**
 * Employee hub — the screen you land on when you tap a team member.
 *
 * Rather than one very long form, the record is split into five categories, each
 * shown as a tile with a one-line recap of what's currently set. Tapping a tile
 * opens a focused screen that saves only that slice (the employee PATCH is
 * partial, so the untouched categories are never overwritten).
 *
 * Route name kept as `employee-edit` — the staff list already links here.
 */
export default function EmployeeHub() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string; email?: string; role?: Role }>();
  const detail = useEmployee(params.id);
  const balances = useAdminLeaveBalances(params.id);

  const e = detail.data;
  const name = e?.fullName ?? params.name ?? 'Employee';
  const email = e?.email ?? params.email;
  const role = e?.role ?? params.role;
  const initial = (name || '?').charAt(0).toUpperCase();

  const categories: Category[] = e
    ? [
        {
          key: 'profile',
          title: 'Profile',
          icon: 'user',
          color: palette.coral,
          background: tint.coral,
          summary: profileSummary(e),
          href: { pathname: '/admin/employee-profile', params: { id: e.id, name: e.fullName } },
        },
        {
          key: 'employment',
          title: 'Employment',
          icon: 'briefcase',
          color: palette.sage,
          background: tint.sage,
          summary: employmentSummary(e),
          href: { pathname: '/admin/employee-employment', params: { id: e.id, name: e.fullName } },
        },
        {
          key: 'compensation',
          title: 'Compensation',
          icon: 'wallet',
          color: palette.amber,
          background: tint.amber,
          summary: compensationSummary(e),
          href: { pathname: '/admin/employee-compensation', params: { id: e.id, name: e.fullName } },
        },
        {
          key: 'statutory',
          title: 'Statutory & bank',
          icon: 'shield',
          color: palette.violet,
          background: tint.violet,
          summary: statutorySummary(e),
          href: { pathname: '/admin/employee-statutory', params: { id: e.id, name: e.fullName } },
        },
        {
          key: 'leave',
          title: 'Leave entitlement',
          icon: 'sun',
          color: palette.soft,
          background: tint.neutral,
          summary: leaveSummary(balances.data ?? []),
          pending: balances.isLoading,
          href: { pathname: '/admin/employee-leave', params: { id: e.id, name: e.fullName } },
        },
      ]
    : [];

  return (
    <Screen>
      <ScreenHeader back title="Employee" />

      {/* identity — name, read-only email, role */}
      <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, padding: spacing.lg, borderRadius: radius['2xl'] }}>
        <Avatar initial={initial} size={52} tint={{ bg: tint.neutral, fg: palette.soft }} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[font(700), { fontSize: 15, color: palette.ink }]} numberOfLines={1}>
            {name}
          </Text>
          {!!email && (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 5 }}>
              <Icon name="mail" size={13} color={palette.faint} />
              <Text style={[font(500), { fontSize: 12, color: palette.faint }]} numberOfLines={1}>
                {email}
              </Text>
            </View>
          )}
        </View>
        {!!role && (
          <Chip label={ROLE_LABEL[role]} color={ROLE_TONE[role].color} background={ROLE_TONE[role].bg} size="sm" />
        )}
      </Card>

      <Text style={[font(500), { fontSize: 12, color: palette.faint, marginTop: spacing.lg, lineHeight: 18 }]}>
        Pick a section to edit. Each one saves on its own — the rest stays as it is.
      </Text>

      <View style={{ marginTop: spacing.md }}>
        <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch} minHeight={280}>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            {categories.map((c) => (
              <Card
                key={c.key}
                padding={0}
                style={{
                  width: '47%',
                  flexGrow: 1,
                  minHeight: 138,
                  padding: spacing.lg,
                  borderRadius: radius['2xl'],
                  justifyContent: 'space-between',
                }}
                onPress={() => router.push(c.href)}
              >
                <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                  <IconTile icon={c.icon} color={c.color} background={c.background} size={40} />
                  <Icon name="chevR" size={16} color={palette.inactive} />
                </View>
                <View style={{ marginTop: spacing.md }}>
                  <Text style={[font(700), { fontSize: 13.5, color: palette.ink, lineHeight: 18 }]} numberOfLines={2}>
                    {c.title}
                  </Text>
                  <Text
                    style={[
                      font(500),
                      {
                        fontSize: 11.5,
                        lineHeight: 16,
                        marginTop: 5,
                        color: c.summary ? palette.soft : palette.inactive,
                      },
                    ]}
                    numberOfLines={2}
                  >
                    {c.pending ? '' : (c.summary ?? 'Not set')}
                  </Text>
                </View>
              </Card>
            ))}
          </View>
        </AsyncBoundary>
      </View>
    </Screen>
  );
}
