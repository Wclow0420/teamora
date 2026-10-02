import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, ScreenHeader, TextField } from '@/components/ui';
import { CompensationFields } from '@/components/CompensationFields';
import { useEmployee, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { PayBasis } from '@/api/types';
import { parseHours, parseSalary } from '@/lib/employeeFields';
import { NO_WORKING_DAYS_ERROR, WEEKDAYS_MASK, isEmptyMask } from '@/lib/workweek';
import { palette, font, spacing } from '@/theme';

/**
 * Employee hub → **Compensation**. Owns the monthly salary and the pay
 * schedule (basis, working days, hours/day) that the daily and hourly rates
 * derive from. Saves only those fields.
 */
export default function EmployeeCompensation() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  const update = useUpdateEmployee();

  const [salary, setSalary] = useState('');
  const [payBasis, setPayBasis] = useState<PayBasis>('MONTHLY');
  const [workingDays, setWorkingDays] = useState<number>(WEEKDAYS_MASK);
  const [hoursPerDay, setHoursPerDay] = useState('8');
  const [seeded, setSeeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seeded || !detail.data) return;
    // The detail endpoint returns the *effective* values (own override else the
    // company default), so it is the only seed this screen needs.
    const d = detail.data;
    setSalary(d.monthlySalary != null ? String(d.monthlySalary) : '');
    setPayBasis(d.payBasis);
    setWorkingDays(d.workingDays);
    setHoursPerDay(String(d.hoursPerDay));
    setSeeded(true);
  }, [seeded, detail.data]);

  const onSave = async () => {
    if (update.isPending) return;
    setError(null);
    const monthlySalary = parseSalary(salary);
    if (salary.trim() && monthlySalary === undefined) {
      setError('Enter a valid monthly salary, or leave it blank.');
      return;
    }
    if (isEmptyMask(workingDays)) {
      setError(NO_WORKING_DAYS_ERROR);
      return;
    }
    const hours = parseHours(hoursPerDay);
    if (hoursPerDay.trim() && hours === undefined) {
      setError('Enter a valid number of hours per day.');
      return;
    }
    try {
      await update.mutateAsync({
        id: params.id,
        body: { monthlySalary, payBasis, workingDays, hoursPerDay: hours },
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Compensation" subtitle={detail.data?.fullName ?? params.name} />

      <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch}>
        <View style={{ gap: 14 }}>
          <TextField
            label="Monthly salary (RM)"
            value={salary}
            onChangeText={setSalary}
            keyboardType="decimal-pad"
            placeholder="e.g. 3500"
          />

          <CompensationFields
            monthlySalary={parseSalary(salary) ?? null}
            payBasis={payBasis}
            onPayBasis={setPayBasis}
            workingDays={workingDays}
            onWorkingDays={setWorkingDays}
            hoursPerDay={hoursPerDay}
            onHoursPerDay={setHoursPerDay}
          />

          {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

          <View style={{ marginTop: spacing.sm }}>
            <Button
              label={update.isPending ? 'Saving…' : 'Save compensation'}
              icon="check"
              disabled={update.isPending || isEmptyMask(workingDays)}
              onPress={onSave}
            />
          </View>
        </View>
      </AsyncBoundary>
    </Screen>
  );
}
