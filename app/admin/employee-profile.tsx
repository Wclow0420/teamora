import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, ScreenHeader, TextField } from '@/components/ui';
import { useEmployee, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette, font, spacing } from '@/theme';

/** Server limit for staff ID and phone. */
const MAX_FIELD_LENGTH = 32;

/**
 * Employee hub → **Profile**. Owns the person's name, staff ID, phone and where
 * they sit in the org chart's labels (job title, department) — nothing else, so
 * saving here can never disturb their manager, work site, pay or statutory details.
 */
export default function EmployeeProfile() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  const update = useUpdateEmployee();

  const [fullName, setFullName] = useState(params.name ?? '');
  const [jobTitle, setJobTitle] = useState('');
  const [department, setDepartment] = useState('');
  const [staffId, setStaffId] = useState('');
  const [phone, setPhone] = useState('');
  const [staffIdError, setStaffIdError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [seeded, setSeeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seeded || !detail.data) return;
    setFullName(detail.data.fullName);
    setJobTitle(detail.data.jobTitle ?? '');
    setDepartment(detail.data.department ?? '');
    setStaffId(detail.data.staffId ?? '');
    setPhone(detail.data.phone ?? '');
    setSeeded(true);
  }, [seeded, detail.data]);

  const onSave = async () => {
    if (update.isPending || !detail.data) return;
    setError(null);
    setStaffIdError(null);
    setPhoneError(null);
    if (staffId.trim().length > MAX_FIELD_LENGTH) {
      setStaffIdError(`Keep it to ${MAX_FIELD_LENGTH} characters or fewer.`);
      return;
    }
    if (phone.trim().length > MAX_FIELD_LENGTH) {
      setPhoneError(`Keep it to ${MAX_FIELD_LENGTH} characters or fewer.`);
      return;
    }
    if (!fullName.trim()) {
      setError('Name is required.');
      return;
    }
    try {
      await update.mutateAsync({
        id: params.id,
        body: {
          fullName: fullName.trim(),
          jobTitle: jobTitle.trim(),
          department: department.trim(),
          // A blank phone clears it. Staff ID is only sent when it changed, so an
          // untouched (or empty) ID can never trip the "already in use" check.
          phone: phone.trim(),
          ...(staffId.trim() !== (detail.data.staffId ?? '') ? { staffId: staffId.trim() } : {}),
        },
      });
      router.back();
    } catch (e) {
      const message = e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.';
      // A duplicate staff ID belongs under that field.
      if (e instanceof ApiError && /staff\s*id/i.test(message)) setStaffIdError(message);
      else setError(message);
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Profile" subtitle={detail.data?.fullName ?? params.name} />

      <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch}>
        <View style={{ gap: 14 }}>
          <TextField label="Full name" value={fullName} onChangeText={setFullName} autoCapitalize="words" />
          <TextField label="Job title" value={jobTitle} onChangeText={setJobTitle} placeholder="e.g. Sales Executive" />
          <TextField label="Department" value={department} onChangeText={setDepartment} placeholder="e.g. Retail" />
          <TextField
            label="Staff ID"
            value={staffId}
            onChangeText={setStaffId}
            autoCapitalize="characters"
            placeholder="e.g. LF-0012"
            helper="Must be unique within your company."
            error={staffIdError}
          />
          <TextField
            label="Phone"
            icon="phone"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
            placeholder="e.g. 012-345 6789"
            helper="Leave blank to remove it."
            error={phoneError}
          />

          {!!detail.data?.email && (
            <Text style={[font(500), { fontSize: 11.5, color: palette.faint, lineHeight: 16 }]}>
              Work email ({detail.data.email}) can't be changed here — it's the employee's sign-in.
            </Text>
          )}

          {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

          <View style={{ marginTop: spacing.sm }}>
            <Button
              label={update.isPending ? 'Saving…' : 'Save profile'}
              icon="check"
              disabled={update.isPending}
              onPress={onSave}
            />
          </View>
        </View>
      </AsyncBoundary>
    </Screen>
  );
}
