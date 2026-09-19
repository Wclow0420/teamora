import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, DateField, toISODate, type SelectOption } from '@/components/ui';
import { useApplyLeave } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette } from '@/theme';

type LeaveType = 'ANNUAL' | 'MEDICAL' | 'EMERGENCY';

const LEAVE_TYPES: SelectOption<LeaveType>[] = [
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'MEDICAL', label: 'Medical' },
  { value: 'EMERGENCY', label: 'Emergency' },
];

export default function LeaveApply() {
  const router = useRouter();
  const apply = useApplyLeave();

  const today = new Date();
  const [leaveType, setLeaveType] = useState<LeaveType>('ANNUAL');
  const [start, setStart] = useState<Date>(today);
  const [end, setEnd] = useState<Date>(today);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const onStartChange = (d: Date) => {
    setStart(d);
    if (end < d) setEnd(d);
  };

  const onSubmit = async () => {
    setError(null);
    if (end < start) {
      setError("End date can't be before the start date.");
      return;
    }
    try {
      await apply.mutateAsync({
        leaveType,
        startDate: toISODate(start),
        endDate: toISODate(end),
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

      <View style={{ gap: 14, marginTop: 8 }}>
        <SelectChips label="Leave type" options={LEAVE_TYPES} value={leaveType} onChange={setLeaveType} />
        <DateField label="Start date" value={start} onChange={onStartChange} minimumDate={today} />
        <DateField label="End date" value={end} onChange={setEnd} minimumDate={start} />
        <TextField label="Reason (optional)" value={reason} onChangeText={setReason} multiline />

        {error && <Text style={{ fontSize: 13, color: palette.danger }}>{error}</Text>}

        <Button
          label={apply.isPending ? 'Submitting…' : 'Submit request'}
          onPress={onSubmit}
          disabled={apply.isPending}
          style={{ marginTop: 4 }}
        />
      </View>
    </Screen>
  );
}
