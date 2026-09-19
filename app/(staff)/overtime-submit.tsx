import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Chip, DateField, ScreenHeader, TextField, toISODate } from '@/components/ui';
import { useOvertime, useSubmitOvertime } from '@/api/queries';
import { statusAccent } from '@/api/accents';
import { ApiError } from '@/api/client';
import { palette, font, radius } from '@/theme';

export default function OvertimeSubmit() {
  const router = useRouter();
  const submit = useSubmitOvertime();
  const mine = useOvertime();

  const [date, setDate] = useState(new Date());
  const [hours, setHours] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    if (submit.isPending) return;
    setError(null);
    const h = parseFloat(hours.replace(',', '.'));
    if (Number.isNaN(h) || h < 0.5 || h > 24) {
      setError('Enter hours between 0.5 and 24.');
      return;
    }
    try {
      await submit.mutateAsync({ workDate: toISODate(date), hours: h, reason: reason.trim() || undefined });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not submit. Check your connection.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Log overtime" />

      <View style={{ gap: 14, marginTop: 8 }}>
        <DateField label="Date worked" value={date} onChange={setDate} maximumDate={new Date()} />
        <TextField label="Hours" icon="clock" value={hours} onChangeText={setHours} placeholder="e.g. 2.5" keyboardType="decimal-pad" />
        <TextField label="Reason (optional)" value={reason} onChangeText={setReason} placeholder="What did you work on?" multiline />
        {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}
        <Button label={submit.isPending ? 'Submitting…' : 'Submit overtime'} icon="check" disabled={submit.isPending} onPress={onSubmit} />
      </View>

      {/* recent overtime */}
      <Text style={[font(700), { fontSize: 14, color: palette.ink, marginTop: 24, marginBottom: 11 }]}>Your overtime</Text>
      <AsyncBoundary loading={mine.isLoading} error={mine.error} onRetry={mine.refetch} minHeight={80}>
        {mine.data && (mine.data.length === 0 ? (
          <Text style={[font(500), { fontSize: 12.5, color: palette.faint }]}>No overtime logged yet.</Text>
        ) : (
          <Card padding={0} style={{ paddingHorizontal: 16 }}>
            {mine.data.map((o, i) => {
              const accent = statusAccent(o.status);
              return (
                <View key={o.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 13, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]}>{o.hoursLabel} · {o.workDateLabel}</Text>
                    {o.reason && <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]} numberOfLines={1}>{o.reason}</Text>}
                  </View>
                  <Chip label={o.statusLabel} background={accent.bg} color={accent.color} size="sm" />
                </View>
              );
            })}
          </Card>
        ))}
      </AsyncBoundary>
    </Screen>
  );
}
