import React, { useState } from 'react';
import { View, Text, Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Avatar, Button, Card, Chip, Icon, ScreenHeader, SelectChips, TextField, type SelectOption } from '@/components/ui';
import { ReportingManagerField } from '@/components/ReportingManagerField';
import { useMe, useTransferOwnership, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { MaritalStatus, Role } from '@/api/types';
import { palette, font, tint } from '@/theme';

type EditableRole = Extract<Role, 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN'>;

const ROLE_OPTIONS: SelectOption<EditableRole>[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR_ADMIN', label: 'HR Admin' },
];

const MARITAL_OPTIONS: SelectOption<MaritalStatus>[] = [
  { value: 'SINGLE', label: 'Single' },
  { value: 'MARRIED', label: 'Married' },
];

const SPOUSE_OPTIONS: SelectOption<'WORKING' | 'NOT_WORKING'>[] = [
  { value: 'NOT_WORKING', label: 'Not working' },
  { value: 'WORKING', label: 'Working' },
];

/** Parse a salary input → a non-negative number, or undefined if blank/invalid. */
function parseSalary(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

/** Parse a children-count input → a non-negative integer, or undefined if blank/invalid. */
function parseChildren(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isInteger(n) && n >= 0 ? n : undefined;
}

export default function EmployeeEdit() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    id: string;
    name: string;
    email?: string;
    jobTitle?: string;
    department?: string;
    role: Role;
    reportingManagerId?: string;
    monthlySalary?: string;
    maritalStatus?: string;
    spouseWorking?: string;
    numChildren?: string;
  }>();
  const me = useMe();
  const update = useUpdateEmployee();
  const transfer = useTransferOwnership();

  const targetIsOwner = params.role === 'OWNER';
  const iAmOwner = me.data?.role === 'OWNER';
  const isSelf = me.data?.id === params.id;
  const canTransfer = iAmOwner && !targetIsOwner && !isSelf;

  const [fullName, setFullName] = useState(params.name ?? '');
  const [jobTitle, setJobTitle] = useState(params.jobTitle ?? '');
  const [department, setDepartment] = useState(params.department ?? '');
  const [salary, setSalary] = useState(params.monthlySalary ?? '');
  const [role, setRole] = useState<EditableRole>(targetIsOwner ? 'HR_ADMIN' : ((params.role as EditableRole) ?? 'EMPLOYEE'));
  const [reportingManagerId, setReportingManagerId] = useState<string | undefined>(params.reportingManagerId || undefined);
  const [maritalStatus, setMaritalStatus] = useState<MaritalStatus>(params.maritalStatus === 'MARRIED' ? 'MARRIED' : 'SINGLE');
  const [spouseWorking, setSpouseWorking] = useState<'WORKING' | 'NOT_WORKING'>(params.spouseWorking === 'true' ? 'WORKING' : 'NOT_WORKING');
  const [children, setChildren] = useState(params.numChildren ?? '');
  const [error, setError] = useState<string | null>(null);

  const initial = (params.name ?? '?').charAt(0).toUpperCase();

  const onSave = async () => {
    if (update.isPending) return;
    setError(null);
    if (!fullName.trim()) {
      setError('Name is required.');
      return;
    }
    const monthlySalary = parseSalary(salary);
    if (salary.trim() && monthlySalary === undefined) {
      setError('Enter a valid monthly salary, or leave it blank.');
      return;
    }
    const numChildren = parseChildren(children);
    if (children.trim() && numChildren === undefined) {
      setError('Enter a valid number of children, or leave it blank.');
      return;
    }
    try {
      await update.mutateAsync({
        id: params.id,
        body: {
          fullName: fullName.trim(),
          jobTitle: jobTitle.trim(),
          department: department.trim(),
          monthlySalary,
          reportingManagerId,
          maritalStatus,
          spouseWorking: maritalStatus === 'MARRIED' ? spouseWorking === 'WORKING' : undefined,
          numChildren,
          // Never send a role for the owner (changed only via transfer).
          role: targetIsOwner ? undefined : role,
        },
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.');
    }
  };

  const onTransfer = () => {
    Alert.alert(
      'Transfer ownership?',
      `${params.name} will become the company owner, and you'll become an HR Admin. This can't be undone by you afterwards.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Transfer',
          style: 'destructive',
          onPress: async () => {
            try {
              await transfer.mutateAsync(params.id);
              Alert.alert('Ownership transferred', `${params.name} is now the owner.`);
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
      <ScreenHeader back title="Edit employee" />

      {/* identity + email (read-only) */}
      <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16, borderRadius: 20 }}>
        <Avatar initial={initial} size={52} tint={{ bg: tint.neutral, fg: palette.soft }} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[font(700), { fontSize: 15, color: palette.ink }]} numberOfLines={1}>
            {fullName || params.name}
          </Text>
          {!!params.email && (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 5 }}>
              <Icon name="mail" size={13} color={palette.faint} />
              <Text style={[font(500), { fontSize: 12, color: palette.faint }]} numberOfLines={1}>
                {params.email}
              </Text>
            </View>
          )}
        </View>
      </Card>

      <View style={{ gap: 14, marginTop: 16 }}>
        <TextField label="Full name" value={fullName} onChangeText={setFullName} autoCapitalize="words" />
        <TextField label="Job title" value={jobTitle} onChangeText={setJobTitle} placeholder="e.g. Sales Executive" />
        <TextField label="Department" value={department} onChangeText={setDepartment} placeholder="e.g. Retail" />
        <TextField
          label="Monthly salary (RM)"
          value={salary}
          onChangeText={setSalary}
          keyboardType="decimal-pad"
          placeholder="e.g. 3500"
        />
        {targetIsOwner ? (
          <View>
            <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>Role</Text>
            <Chip label="Owner" background={tint.violet} color={palette.violet} leading={<Icon name="shield" size={13} color={palette.violet} />} />
          </View>
        ) : (
          <SelectChips label="Role" options={ROLE_OPTIONS} value={role} onChange={setRole} />
        )}
        <ReportingManagerField value={reportingManagerId} onChange={setReportingManagerId} />

        <View style={{ gap: 14, marginTop: 4 }}>
          <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Tax profile</Text>
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
            Used to estimate monthly income tax (PCB) on payslips.
          </Text>
          <SelectChips label="Marital status" options={MARITAL_OPTIONS} value={maritalStatus} onChange={setMaritalStatus} />
          {maritalStatus === 'MARRIED' && (
            <SelectChips label="Spouse" options={SPOUSE_OPTIONS} value={spouseWorking} onChange={setSpouseWorking} />
          )}
          <TextField
            label="Number of children"
            value={children}
            onChangeText={setChildren}
            keyboardType="number-pad"
            placeholder="e.g. 2"
          />
        </View>

        {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}
      </View>

      <View style={{ marginTop: 22, gap: 12 }}>
        <Button label={update.isPending ? 'Saving…' : 'Save changes'} icon="check" disabled={update.isPending} onPress={onSave} />
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
    </Screen>
  );
}
