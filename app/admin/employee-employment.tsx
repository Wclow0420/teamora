import React, { useEffect, useState } from 'react';
import { View, Text, Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Chip, DateField, Icon, ScreenHeader, SelectChips, toISODate, type SelectOption } from '@/components/ui';
import { ReportingManagerField } from '@/components/ReportingManagerField';
import { WorkLocationField } from '@/components/WorkLocationField';
import { useEmployee, useMe, useTransferOwnership, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import { isAdminRole, type Role, type UpdateEmployeeBody } from '@/api/types';
import { palette, font, spacing, tint } from '@/theme';

type EditableRole = Extract<Role, 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN'>;

const ROLE_OPTIONS: SelectOption<EditableRole>[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR_ADMIN', label: 'HR Admin' },
];

/** Parse an ISO yyyy-MM-dd into a local Date (not UTC-shifted), or null. */
function parseIsoDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
}

/**
 * Employee hub → **Employment**. Owns the role, the reporting manager (who
 * approves their requests), the assigned work site, and the ownership transfer.
 *
 * This is the ONLY screen that touches the manager / work-location fields, and
 * it uses the PATCH clear flags for them: an id sets the link, `clear…: true`
 * removes it, and any other screen simply omits both so the links survive.
 */
export default function EmployeeEmployment() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  const me = useMe();
  const update = useUpdateEmployee();
  const transfer = useTransferOwnership();

  const name = detail.data?.fullName ?? params.name ?? 'Employee';
  const targetIsOwner = detail.data?.role === 'OWNER';
  const iAmOwner = me.data?.role === 'OWNER';
  const isSelf = me.data?.id === params.id;
  const canTransfer = iAmOwner && !targetIsOwner && !isSelf;
  // OWNER/HR_ADMIN can set a temporary password for anyone but the owner (the
  // server 403s an HR admin resetting the owner). Your own goes via Profile.
  const canResetPassword = !!detail.data && isAdminRole(me.data?.role) && !targetIsOwner && !isSelf;

  const [role, setRole] = useState<EditableRole>('EMPLOYEE');
  const [reportingManagerId, setReportingManagerId] = useState<string | undefined>(undefined);
  const [workLocationId, setWorkLocationId] = useState<string | undefined>(undefined);
  const [joinDate, setJoinDate] = useState<Date | null>(null);
  const [seeded, setSeeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seeded || !detail.data) return;
    const d = detail.data;
    setRole(d.role === 'OWNER' ? 'HR_ADMIN' : (d.role as EditableRole));
    setReportingManagerId(d.reportingManagerId ?? undefined);
    setWorkLocationId(d.workLocationId ?? undefined);
    setJoinDate(parseIsoDate(d.joinDate));
    setSeeded(true);
  }, [seeded, detail.data]);

  const onSave = async () => {
    if (update.isPending) return;
    setError(null);
    const body: UpdateEmployeeBody = {
      // Never send a role for the owner — that only changes via transfer.
      role: targetIsOwner ? undefined : role,
    };
    // Set by id, or clear explicitly. Omitting both would leave it unchanged —
    // which is what every other sub-screen does on purpose.
    if (reportingManagerId) body.reportingManagerId = reportingManagerId;
    else body.clearReportingManager = true;
    if (workLocationId) body.workLocationId = workLocationId;
    else body.clearWorkLocation = true;
    if (joinDate) body.joinDate = toISODate(joinDate);

    try {
      await update.mutateAsync({ id: params.id, body });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.');
    }
  };

  const onTransfer = () => {
    Alert.alert(
      'Transfer ownership?',
      `${name} will become the company owner, and you'll become an HR Admin. This can't be undone by you afterwards.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Transfer',
          style: 'destructive',
          onPress: async () => {
            try {
              await transfer.mutateAsync(params.id);
              Alert.alert('Ownership transferred', `${name} is now the owner.`);
              router.back();
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'Could not transfer ownership.');
            }
          },
        },
      ],
    );
  };

  return (
    <Screen>
      <ScreenHeader back title="Employment" subtitle={name} />

      <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch}>
        <View style={{ gap: 14 }}>
          {targetIsOwner ? (
            <View>
              <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>Role</Text>
              <View style={{ flexDirection: 'row' }}>
                <Chip
                  label="Owner"
                  background={tint.violet}
                  color={palette.violet}
                  leading={<Icon name="shield" size={13} color={palette.violet} />}
                />
              </View>
              <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 7, lineHeight: 16 }]}>
                A company has exactly one owner. Change it by transferring ownership from the owner's own account.
              </Text>
            </View>
          ) : (
            <SelectChips label="Role" options={ROLE_OPTIONS} value={role} onChange={setRole} />
          )}

          <ReportingManagerField value={reportingManagerId} onChange={setReportingManagerId} />
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
            The manager who approves this employee's leave, claims and overtime. With none, the company owner
            approves.
          </Text>

          <WorkLocationField
            value={workLocationId}
            onChange={setWorkLocationId}
            currentName={detail.data?.workLocationName ?? undefined}
          />

          <View>
            <DateField
              label="Joined"
              value={joinDate ?? new Date()}
              onChange={setJoinDate}
              maximumDate={new Date()}
            />
            <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 7, lineHeight: 16 }]}>
              {joinDate
                ? 'Prorates their first-year leave entitlement.'
                : 'Not recorded yet — pick a date to prorate their first-year leave entitlement.'}
            </Text>
          </View>

          {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

          <View style={{ marginTop: spacing.sm, gap: 12 }}>
            <Button
              label={update.isPending ? 'Saving…' : 'Save employment'}
              icon="check"
              disabled={update.isPending}
              onPress={onSave}
            />
            {canResetPassword && (
              <Button
                label="Reset password"
                icon="lock"
                variant="light"
                onPress={() =>
                  router.push({ pathname: '/admin/employee-reset-password', params: { id: params.id, name } })
                }
              />
            )}
            {canTransfer && (
              <Button
                label={transfer.isPending ? 'Transferring…' : 'Transfer ownership'}
                icon="shield"
                variant="light"
                disabled={transfer.isPending}
                onPress={onTransfer}
              />
            )}
          </View>
        </View>
      </AsyncBoundary>
    </Screen>
  );
}
