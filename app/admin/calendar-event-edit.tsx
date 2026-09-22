import React, { useState } from 'react';
import { View, Text, Pressable, Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, DateField, Icon, SelectChips, TextField, toISODate, type SelectOption } from '@/components/ui';
import { useCreateCompanyEvent, useDeleteCompanyEvent, useUpdateCompanyEvent } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { EventTypeValue } from '@/api/types';
import { palette, font } from '@/theme';

const TYPE_OPTIONS: SelectOption<EventTypeValue>[] = [
  { value: 'HOLIDAY', label: 'Holiday' },
  { value: 'EVENT', label: 'Event' },
  { value: 'TOWNHALL', label: 'Townhall' },
];

const MAX_TIME_LABEL = 64;

/** Parse a YYYY-MM-DD param into a local Date, falling back to today. */
function parseDate(iso: string | undefined): Date {
  if (!iso) return new Date();
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return new Date();
  return new Date(y, m - 1, d);
}

/** Narrow a route param to a supported event type (defaults to HOLIDAY). */
function parseType(v: string | undefined): EventTypeValue {
  return v === 'EVENT' || v === 'TOWNHALL' ? v : 'HOLIDAY';
}

export default function CalendarEventEdit() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    id?: string;
    title?: string;
    eventDate?: string;
    eventType?: string;
    timeLabel?: string;
  }>();
  const isEdit = !!params.id;

  const create = useCreateCompanyEvent();
  const update = useUpdateCompanyEvent();
  const remove = useDeleteCompanyEvent();

  const [title, setTitle] = useState(params.title ?? '');
  const [date, setDate] = useState<Date>(parseDate(params.eventDate));
  const [eventType, setEventType] = useState<EventTypeValue>(parseType(params.eventType));
  const [timeLabel, setTimeLabel] = useState(params.timeLabel ?? '');
  const [error, setError] = useState<string | null>(null);

  const busy = create.isPending || update.isPending || remove.isPending;

  const onSubmit = async () => {
    setError(null);
    const name = title.trim();
    if (!name) {
      setError('Give the event a title.');
      return;
    }
    if (name.length > 255) {
      setError('Title is too long (max 255 characters).');
      return;
    }
    const time = timeLabel.trim();
    if (time.length > MAX_TIME_LABEL) {
      setError(`Time label is too long (max ${MAX_TIME_LABEL} characters).`);
      return;
    }
    try {
      if (isEdit && params.id) {
        await update.mutateAsync({
          id: params.id,
          // An empty string clears the label server-side; `null` would mean "leave unchanged".
          body: { title: name, eventDate: toISODate(date), eventType, timeLabel: time },
        });
      } else {
        await create.mutateAsync({
          title: name,
          eventDate: toISODate(date),
          eventType,
          timeLabel: time || null,
        });
      }
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  const onDelete = () => {
    if (!params.id) return;
    const id = params.id;
    Alert.alert(
      'Delete this event?',
      `"${params.title ?? title}" will be removed from everyone's calendar.${
        eventType === 'HOLIDAY' ? ' It will no longer be paid as a public holiday in payroll.' : ''
      }`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            setError(null);
            try {
              await remove.mutateAsync(id);
              router.back();
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'Could not delete this event. Please try again.');
            }
          },
        },
      ],
    );
  };

  return (
    <Screen>
      <ScreenHeader back title={isEdit ? 'Edit event' : 'New event'} />

      <View style={{ gap: 14, marginTop: 8 }}>
        <TextField label="Title" value={title} onChangeText={setTitle} placeholder="e.g. Hari Raya Aidilfitri" />

        <DateField label="Date" value={date} onChange={setDate} />

        <SelectChips label="Type" options={TYPE_OPTIONS} value={eventType} onChange={setEventType} />
        {eventType === 'HOLIDAY' && (
          <View style={{ flexDirection: 'row', gap: 7, marginTop: -8 }}>
            <Icon name="shield" size={14} color={palette.faint} />
            <Text style={[font(500), { flex: 1, fontSize: 11.5, color: palette.faint, lineHeight: 16 }]}>
              Public holidays are paid and are excluded from unpaid-leave deductions in payroll.
            </Text>
          </View>
        )}

        <TextField
          label="Time label (optional)"
          value={timeLabel}
          onChangeText={setTimeLabel}
          placeholder="e.g. 10:00 AM – 12:00 PM"
        />

        {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

        <Button
          label={busy ? 'Saving…' : isEdit ? 'Save changes' : 'Create event'}
          onPress={onSubmit}
          disabled={busy}
          style={{ marginTop: 4 }}
        />

        {isEdit && (
          <Pressable
            onPress={onDelete}
            disabled={busy}
            accessibilityRole="button"
            style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 10 }}
          >
            <Icon name="x" size={17} color={palette.danger} />
            <Text style={[font(700), { fontSize: 13.5, color: palette.danger }]}>Delete event</Text>
          </Pressable>
        )}
      </View>
    </Screen>
  );
}
