import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, ScreenHeader, TextField } from '@/components/ui';
import { CompensationFields, type CompensationDefaults, type PayBasisChoice } from '@/components/CompensationFields';
import { useCompanySettings, useEmployee, useUpdateEmployee } from '@/api/queries';
import type { UpdateEmployeeBody } from '@/api/types';
import { parseHours, parseSalary } from '@/lib/employeeFields';
import { errorMessage } from '@/lib/errors';
import { NO_WORKING_DAYS_ERROR, WEEKDAYS_MASK, isEmptyMask } from '@/lib/workweek';
import { palette, font, spacing } from '@/theme';

/** What the server had when the form opened — the baseline for "what changed". */
type Seed = { salary: number | null } & CompensationDefaults;

/**
 * Employee hub → **Compensation**. Owns the monthly salary and the pay
 * schedule (basis, working days, hours/day) that the daily and hourly rates
 * derive from.
 *
 * The save is *partial*: only what the admin actually changed is sent, so an
 * untouched field keeps following the company default instead of being pinned
 * to today's value. Emptying a field (or picking "company default") sends its
 * `clear…` flag, which drops the employee's own override.
 */
export default function EmployeeCompensation() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  // Only used to name the defaults in the options — the form works without it.
  const settings = useCompanySettings();
  const update = useUpdateEmployee();

  const [salary, setSalary] = useState('');
  const [payBasis, setPayBasis] = useState<PayBasisChoice>('MONTHLY');
  const [workingDays, setWorkingDays] = useState<number>(WEEKDAYS_MASK);
  const [useDefaultDays, setUseDefaultDays] = useState(false);
  const [hoursPerDay, setHoursPerDay] = useState('');
  const [seed, setSeed] = useState<Seed | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seed || !detail.data) return;
    // The detail endpoint returns the *effective* values (own override else the
    // company default), so it is the only seed this screen needs.
    const d = detail.data;
    setSalary(d.monthlySalary != null ? String(d.monthlySalary) : '');
    setPayBasis(d.payBasis);
    setWorkingDays(d.workingDays);
    setHoursPerDay(String(d.hoursPerDay));
    setSeed({ salary: d.monthlySalary, payBasis: d.payBasis, workingDays: d.workingDays, hoursPerDay: d.hoursPerDay });
  }, [seed, detail.data]);

  const defaults: CompensationDefaults | null = settings.data
    ? {
        payBasis: settings.data.defaultPayBasis,
        workingDays: settings.data.defaultWorkingDays,
        hoursPerDay: settings.data.defaultHoursPerDay,
      }
    : null;

  const noWorkingDays = !useDefaultDays && isEmptyMask(workingDays);

  const onSave = async () => {
    if (update.isPending || !seed) return;
    setError(null);

    const body: UpdateEmployeeBody = {};

    if (!salary.trim()) {
      // Emptied → no salary (not on payroll). Already blank → nothing to do.
      if (seed.salary != null) body.clearMonthlySalary = true;
    } else {
      const monthlySalary = parseSalary(salary);
      if (monthlySalary === undefined) {
        setError('Enter a valid monthly salary, or leave it blank.');
        return;
      }
      if (monthlySalary !== seed.salary) body.monthlySalary = monthlySalary;
    }

    if (payBasis === 'DEFAULT') body.clearPayBasis = true;
    else if (payBasis !== seed.payBasis) body.payBasis = payBasis;

    if (useDefaultDays) {
      body.clearWorkingDays = true;
    } else {
      if (isEmptyMask(workingDays)) {
        setError(NO_WORKING_DAYS_ERROR);
        return;
      }
      if (workingDays !== seed.workingDays) body.workingDays = workingDays;
    }

    if (!hoursPerDay.trim()) {
      body.clearHoursPerDay = true;
    } else {
      const hours = parseHours(hoursPerDay);
      if (hours === undefined) {
        setError('Enter a valid number of hours per day, or leave it blank for the company default.');
        return;
      }
      if (hours !== seed.hoursPerDay) body.hoursPerDay = hours;
    }

    if (Object.keys(body).length === 0) {
      router.back();
      return;
    }
    try {
      await update.mutateAsync({ id: params.id, body });
      router.back();
    } catch (e) {
      setError(errorMessage(e, 'Could not save changes. Check your connection.'));
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Compensation" subtitle={detail.data?.fullName ?? params.name} />

      <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch}>
        {seed && (
          <View style={{ gap: 14 }}>
            <TextField
              label="Monthly salary (RM)"
              value={salary}
              onChangeText={setSalary}
              keyboardType="decimal-pad"
              placeholder="e.g. 3500"
              helper="Leave blank if this person isn't on payroll."
            />

            <CompensationFields
              monthlySalary={parseSalary(salary) ?? null}
              payBasis={payBasis}
              onPayBasis={setPayBasis}
              workingDays={workingDays}
              onWorkingDays={setWorkingDays}
              useDefaultWorkingDays={useDefaultDays}
              onUseDefaultWorkingDays={setUseDefaultDays}
              hoursPerDay={hoursPerDay}
              onHoursPerDay={setHoursPerDay}
              defaults={defaults}
              fallback={seed}
            />

            {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

            <View style={{ marginTop: spacing.sm }}>
              <Button
                label={update.isPending ? 'Saving…' : 'Save compensation'}
                icon="check"
                disabled={update.isPending || noWorkingDays}
                onPress={onSave}
              />
            </View>
          </View>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
