import React, { useEffect, useMemo, useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import {
  ScreenHeader,
  Button,
  TextField,
  SelectChips,
  DateField,
  TimeField,
  Icon,
  toISODate,
  toHHMM,
  type SelectOption,
} from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useApplyLeave, useLeaveTypes, useMe } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { HalfDayPeriod, LeaveDurationUnit } from '@/api/types';
import { formatDecimal, formatHours } from '@/lib/numbers';
import { palette, font, radius, tint } from '@/theme';

const DURATION_OPTIONS: SelectOption<LeaveDurationUnit>[] = [
  { value: 'FULL_DAY', label: 'Full day' },
  { value: 'HALF_DAY', label: 'Half day' },
  { value: 'HOURS', label: 'Hours' },
];

const HALF_DAY_OPTIONS: SelectOption<HalfDayPeriod>[] = [
  { value: 'AM', label: 'Morning (AM)' },
  { value: 'PM', label: 'Afternoon (PM)' },
];

/** Fallback work day length when the profile hasn't loaded yet. */
const FALLBACK_HOURS_PER_DAY = 8;

export default function LeaveApply() {
  const router = useRouter();
  const apply = useApplyLeave();
  const leaveTypes = useLeaveTypes();
  const me = useMe();

  const today = new Date();
  const [leaveTypeId, setLeaveTypeId] = useState<string>('');
  const [unit, setUnit] = useState<LeaveDurationUnit>('FULL_DAY');
  const [halfDayPeriod, setHalfDayPeriod] = useState<HalfDayPeriod>('AM');
  const [hoursText, setHoursText] = useState('');
  const [startTime, setStartTime] = useState<Date | null>(null);
  const [start, setStart] = useState<Date>(today);
  const [end, setEnd] = useState<Date>(today);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Default to the first available leave type once loaded.
  useEffect(() => {
    if (!leaveTypeId && leaveTypes.data && leaveTypes.data.length > 0) {
      setLeaveTypeId(leaveTypes.data[0].id);
    }
  }, [leaveTypeId, leaveTypes.data]);

  const options: SelectOption<string>[] = (leaveTypes.data ?? []).map((t) => ({ value: t.id, label: t.name }));
  const selected = leaveTypes.data?.find((t) => t.id === leaveTypeId);
  const isUnpaid = selected ? !selected.paid : false;

  const hoursPerDay =
    me.data && Number.isFinite(me.data.hoursPerDay) && me.data.hoursPerDay > 0
      ? me.data.hoursPerDay
      : FALLBACK_HOURS_PER_DAY;

  const isFullDay = unit === 'FULL_DAY';
  const hoursValue = useMemo(() => {
    const n = parseFloat(hoursText.replace(',', '.'));
    return Number.isFinite(n) ? n : null;
  }, [hoursText]);

  /** Live "2 hours = 0.25 day of your balance" hint. */
  const hoursHint = useMemo(() => {
    if (unit !== 'HOURS' || hoursValue == null || hoursValue <= 0) {
      return `A full work day here is ${formatHours(hoursPerDay)}.`;
    }
    const fraction = Math.round((hoursValue / hoursPerDay) * 100) / 100;
    return `${formatHours(hoursValue)} = ${formatDecimal(fraction)} day of your balance (${formatHours(hoursPerDay)} per day).`;
  }, [unit, hoursValue, hoursPerDay]);

  const onStartChange = (d: Date) => {
    setStart(d);
    if (end < d) setEnd(d);
  };

  const onUnitChange = (next: LeaveDurationUnit) => {
    setUnit(next);
    setError(null);
    // Partial leave is single-date: keep the range collapsed so the payload matches.
    if (next !== 'FULL_DAY') setEnd(start);
  };

  const onSubmit = async () => {
    setError(null);
    if (!leaveTypeId) {
      setError('Please choose a leave type.');
      return;
    }
    if (isFullDay && end < start) {
      setError("End date can't be before the start date.");
      return;
    }
    if (unit === 'HOURS') {
      if (hoursValue == null || hoursValue <= 0) {
        setError('Enter how many hours you need (more than 0).');
        return;
      }
      if (hoursValue > hoursPerDay) {
        setError(`That's longer than a work day — enter ${formatHours(hoursPerDay)} or less.`);
        return;
      }
    }
    const startDate = toISODate(start);
    try {
      await apply.mutateAsync({
        leaveTypeId,
        startDate,
        endDate: isFullDay ? toISODate(end) : startDate,
        durationUnit: unit,
        halfDayPeriod: unit === 'HALF_DAY' ? halfDayPeriod : undefined,
        hours: unit === 'HOURS' && hoursValue != null ? hoursValue : undefined,
        startTime: unit === 'HOURS' && startTime ? toHHMM(startTime) : undefined,
        reason: reason.trim() || undefined,
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Apply for leave" />

      <AsyncBoundary loading={leaveTypes.isLoading} error={leaveTypes.error} onRetry={leaveTypes.refetch}>
        {leaveTypes.data && (
          <View style={{ gap: 14, marginTop: 8 }}>
            <SelectChips label="Leave type" options={options} value={leaveTypeId} onChange={setLeaveTypeId} />

            {isUnpaid && (
              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 9,
                  padding: 13,
                  borderRadius: radius.lg,
                  backgroundColor: tint.amber,
                }}
              >
                <Icon name="clock" size={17} color={palette.amber} />
                <Text style={[font(600), { flex: 1, fontSize: 12.5, color: palette.soft, lineHeight: 18 }]}>
                  This leave is unpaid — salary will be deducted for the time taken.
                </Text>
              </View>
            )}

            <SelectChips label="Duration" options={DURATION_OPTIONS} value={unit} onChange={onUnitChange} />

            <DateField
              label={isFullDay ? 'Start date' : 'Date'}
              value={start}
              onChange={onStartChange}
              minimumDate={today}
            />
            {isFullDay && <DateField label="End date" value={end} onChange={setEnd} minimumDate={start} />}

            {unit === 'HALF_DAY' && (
              <SelectChips
                label="Which half?"
                options={HALF_DAY_OPTIONS}
                value={halfDayPeriod}
                onChange={setHalfDayPeriod}
              />
            )}

            {unit === 'HOURS' && (
              <>
                <TextField
                  label="Hours"
                  value={hoursText}
                  onChangeText={setHoursText}
                  placeholder={`e.g. 2 (max ${formatDecimal(hoursPerDay)})`}
                  keyboardType="decimal-pad"
                />
                <Text style={[font(600), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
                  {hoursHint}
                </Text>
                <TimeField label="Start time (optional)" value={startTime} onChange={setStartTime} />
              </>
            )}

            <TextField label="Reason (optional)" value={reason} onChangeText={setReason} multiline />

            {error && <Text style={{ fontSize: 13, color: palette.danger }}>{error}</Text>}

            <Button
              label={apply.isPending ? 'Submitting…' : 'Submit request'}
              onPress={onSubmit}
              disabled={apply.isPending}
              style={{ marginTop: 4 }}
            />
          </View>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
