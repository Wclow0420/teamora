import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { ScreenHeader, Button, SelectChips, DateField, toISODate, type SelectOption } from '@/components/ui';
import { useStaff, useAssignShift } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette } from '@/theme';

type ShiftType = 'MORNING' | 'EVENING' | 'REMOTE' | 'OFF';

const SHIFT_OPTIONS: SelectOption<ShiftType>[] = [
  { value: 'MORNING', label: 'Morning' },
  { value: 'EVENING', label: 'Evening' },
  { value: 'REMOTE', label: 'Remote' },
  { value: 'OFF', label: 'Off' },
];

function parseDate(value?: string): Date {
  if (value) {
    const [y, m, d] = value.split('-').map(Number);
    if (y && m && d) return new Date(y, m - 1, d);
  }
  return new Date();
}

export default function ShiftAssign() {
  const router = useRouter();
  const { date: dateParam } = useLocalSearchParams<{ date?: string }>();
  const staff = useStaff();
  const assign = useAssignShift();

  const [employeeId, setEmployeeId] = useState('');
  const [date, setDate] = useState<Date>(() => parseDate(dateParam));
  const [shiftType, setShiftType] = useState<ShiftType>('MORNING');
  const [error, setError] = useState<string | null>(null);

  const employeeOptions: SelectOption<string>[] = (staff.data ?? []).map((e) => ({
    value: e.id,
    label: e.fullName,
  }));

  const onSubmit = async () => {
    setError(null);
    if (!employeeId) {
      setError('Pick an employee.');
      return;
    }
    try {
      await assign.mutateAsync({ employeeId, date: toISODate(date), shiftType });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not assign. Check your connection.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Assign shift" />

      <View style={{ gap: 14, marginTop: 8 }}>
        <AsyncBoundary loading={staff.isLoading} error={staff.error} onRetry={staff.refetch}>
          <SelectChips label="Employee" options={employeeOptions} value={employeeId} onChange={setEmployeeId} />
        </AsyncBoundary>

        <DateField label="Date" value={date} onChange={setDate} />

        <SelectChips label="Shift" options={SHIFT_OPTIONS} value={shiftType} onChange={setShiftType} />

        {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

        <Button
          label={assign.isPending ? 'Assigning…' : 'Assign'}
          onPress={onSubmit}
          disabled={assign.isPending}
        />
      </View>
    </Screen>
  );
}
