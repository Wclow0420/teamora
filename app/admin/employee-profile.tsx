import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, ScreenHeader, TextField } from '@/components/ui';
import { useEmployee, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette, font, spacing } from '@/theme';

/**
 * Employee hub → **Profile**. Owns the person's name and where they sit in the
 * org chart's labels (job title, department) — nothing else, so saving here can
 * never disturb their manager, work site, pay or statutory details.
 */
export default function EmployeeProfile() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  const update = useUpdateEmployee();

  const [fullName, setFullName] = useState(params.name ?? '');
  const [jobTitle, setJobTitle] = useState('');
  const [department, setDepartment] = useState('');
  const [seeded, setSeeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seeded || !detail.data) return;
    setFullName(detail.data.fullName);
    setJobTitle(detail.data.jobTitle ?? '');
    setDepartment(detail.data.department ?? '');
    setSeeded(true);
  }, [seeded, detail.data]);

  const onSave = async () => {
    if (update.isPending) return;
    setError(null);
    if (!fullName.trim()) {
      setError('Name is required.');
      return;
    }
    try {
      await update.mutateAsync({
        id: params.id,
        body: { fullName: fullName.trim(), jobTitle: jobTitle.trim(), department: department.trim() },
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.');
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
