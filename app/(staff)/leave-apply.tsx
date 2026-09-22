import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, DateField, Icon, toISODate, type SelectOption } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useApplyLeave, useLeaveTypes } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette, font, radius, tint } from '@/theme';

export default function LeaveApply() {
  const router = useRouter();
  const apply = useApplyLeave();
  const leaveTypes = useLeaveTypes();

  const today = new Date();
  const [leaveTypeId, setLeaveTypeId] = useState<string>('');
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

  const onStartChange = (d: Date) => {
    setStart(d);
    if (end < d) setEnd(d);
  };

  const onSubmit = async () => {
    setError(null);
    if (!leaveTypeId) {
      setError('Please choose a leave type.');
      return;
    }
    if (end < start) {
      setError("End date can't be before the start date.");
      return;
    }
    try {
      await apply.mutateAsync({
        leaveTypeId,
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
                  This leave is unpaid — salary will be deducted for the days taken.
                </Text>
              </View>
            )}

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
        )}
      </AsyncBoundary>
    </Screen>
  );
}
