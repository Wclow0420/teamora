import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Icon, ScreenHeader, TextField, type IconName } from '@/components/ui';
import { useMe, useUpdateMyDetails } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { EmployeeResponse, Role } from '@/api/types';
import { palette, font, spacing } from '@/theme';

const ROLE_LABEL: Record<Role, string> = {
  OWNER: 'Owner',
  HR_ADMIN: 'HR Admin',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
};

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MAX_PHONE_LENGTH = 32;

/** ISO yyyy-MM-dd → "12 Mar 2024" (parsed as a local date, not UTC-shifted). */
function formatJoinDate(iso: string | null): string | null {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d || m > 12) return null;
  return `${d} ${MONTHS[m - 1]} ${y}`;
}

type Row = { icon: IconName; label: string; value: string | null };

/** What HR has on file. A null value renders "Not set" — never an invented one. */
function detailRows(e: EmployeeResponse): Row[] {
  return [
    { icon: 'mail', label: 'Email', value: e.email },
    { icon: 'doc', label: 'Staff ID', value: e.staffId },
    { icon: 'briefcase', label: 'Job title', value: e.jobTitle },
    { icon: 'building', label: 'Department', value: e.department },
    // No reporting manager → requests route to the company owner (a real rule, not a blank).
    { icon: 'users', label: 'Reporting manager', value: e.reportingManagerName ?? 'Company owner approves' },
    { icon: 'pin', label: 'Work location', value: e.workLocationName },
    { icon: 'calendar', label: 'Joined', value: formatJoinDate(e.joinDate) },
    { icon: 'shield', label: 'Role', value: ROLE_LABEL[e.role] },
  ];
}

/**
 * Profile → **My details**. Read-only view of the employee's own record, plus
 * the one thing they may edit themselves: their phone number
 * (`PATCH /api/employees/me`). Everything else is changed by HR.
 */
export default function MyDetails() {
  const me = useMe();
  const update = useUpdateMyDetails();

  const [phone, setPhone] = useState('');
  const [seeded, setSeeded] = useState(false);
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (seeded || !me.data) return;
    setPhone(me.data.phone ?? '');
    setSeeded(true);
  }, [seeded, me.data]);

  const trimmed = phone.trim();
  const dirty = seeded && trimmed !== (me.data?.phone ?? '');

  const onChangePhone = (t: string) => {
    setPhone(t);
    setSaved(false);
    setPhoneError(null);
  };

  const onSave = async () => {
    if (update.isPending || !dirty) return;
    setPhoneError(null);
    setSaved(false);
    if (trimmed.length > MAX_PHONE_LENGTH) {
      setPhoneError(`Keep it to ${MAX_PHONE_LENGTH} characters or fewer.`);
      return;
    }
    try {
      // A blank value clears the number on the server.
      const res = await update.mutateAsync({ phone: trimmed });
      setPhone(res.phone ?? '');
      setSaved(true);
    } catch (e) {
      setPhoneError(e instanceof ApiError ? e.message : 'Could not save. Check your connection and try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="My details" subtitle={me.data?.fullName} />

      <AsyncBoundary loading={me.isLoading} error={me.error} onRetry={me.refetch}>
        {me.data && (
          <>
            <Card padding={0} style={{ paddingHorizontal: spacing.lg }}>
              {detailRows(me.data).map((r, i) => (
                <View
                  key={r.label}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: spacing.md,
                    paddingVertical: 13,
                    borderTopWidth: i ? 1 : 0,
                    borderTopColor: palette.line,
                  }}
                >
                  <Icon name={r.icon} size={17} color={palette.faint} />
                  <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>{r.label}</Text>
                  <Text
                    style={[
                      r.value ? font(700) : font(500),
                      {
                        flex: 1,
                        textAlign: 'right',
                        fontSize: 13,
                        lineHeight: 18,
                        color: r.value ? palette.ink : palette.inactive,
                      },
                    ]}
                    numberOfLines={2}
                  >
                    {r.value ?? 'Not set'}
                  </Text>
                </View>
              ))}
            </Card>

            <Text style={[font(500), { fontSize: 11.5, lineHeight: 17, color: palette.faint, marginTop: spacing.sm }]}>
              Something wrong here? Only your HR admin can change these — let them know.
            </Text>

            <View style={{ marginTop: spacing.xl, gap: spacing.md }}>
              <TextField
                label="Phone"
                icon="phone"
                value={phone}
                onChangeText={onChangePhone}
                keyboardType="phone-pad"
                placeholder="Not set — e.g. 012-345 6789"
                error={phoneError}
              />
              {saved && (
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <Icon name="check" size={15} color={palette.sage} stroke={2.2} />
                  <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.sage }]}>
                    {trimmed ? 'Phone number saved.' : 'Phone number removed.'}
                  </Text>
                </View>
              )}
              <Button
                label={update.isPending ? 'Saving…' : 'Save phone number'}
                icon="check"
                disabled={update.isPending || !dirty}
                onPress={onSave}
              />
            </View>
          </>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
