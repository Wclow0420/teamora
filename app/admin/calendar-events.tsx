import React, { useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { ScreenHeader, Button, Card, Chip, EmptyState, Icon } from '@/components/ui';
import { useAdminCalendarEvents } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import type { CompanyEventItem, EventTypeValue } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

const TYPE_LABEL: Record<EventTypeValue, string> = {
  HOLIDAY: 'Holiday',
  EVENT: 'Event',
  TOWNHALL: 'Townhall',
};

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Current month as YYYY-MM (local time). */
function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** Step a YYYY-MM key by `delta` months. */
function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, (m - 1) + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** "March 2026" for a YYYY-MM key. */
function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

/** Split an ISO date into its day number + weekday abbreviation. */
function dayParts(iso: string): { day: string; weekday: string } {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return { day: '—', weekday: '' };
  const date = new Date(y, m - 1, d);
  return { day: String(d), weekday: WEEKDAYS[date.getDay()] };
}

/** Round icon button used for the month stepper. */
function StepButton({ icon, label, onPress }: { icon: 'chevL' | 'chevR'; label: string; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={6}
      style={{
        width: 36,
        height: 36,
        borderRadius: radius.md,
        backgroundColor: palette.surface,
        borderWidth: 1,
        borderColor: palette.line,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon name={icon} size={16} color={palette.soft} />
    </Pressable>
  );
}

export default function CalendarEvents() {
  const router = useRouter();
  const [month, setMonth] = useState<string>(currentMonth());
  const q = useAdminCalendarEvents(month);

  const openNew = () => router.push({ pathname: '/admin/calendar-event-edit', params: { eventDate: `${month}-01` } });

  const openEdit = (e: CompanyEventItem) =>
    router.push({
      pathname: '/admin/calendar-event-edit',
      params: {
        id: e.id,
        title: e.title,
        eventDate: e.eventDate,
        eventType: e.eventType,
        timeLabel: e.timeLabel ?? '',
      },
    });

  const events = q.data ?? [];

  return (
    <Screen>
      <ScreenHeader back title="Company calendar" />

      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4, marginBottom: 14, lineHeight: 16 }]}>
        Public holidays, company events and townhalls. Everyone sees these on their
        calendar, and holidays are paid in payroll.
      </Text>

      {/* month stepper */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 14,
        }}
      >
        <StepButton icon="chevL" label="Previous month" onPress={() => setMonth((m) => shiftMonth(m, -1))} />
        <Text style={[font(800), { fontSize: 15, color: palette.ink, letterSpacing: -0.3 }]}>{monthLabel(month)}</Text>
        <StepButton icon="chevR" label="Next month" onPress={() => setMonth((m) => shiftMonth(m, 1))} />
      </View>

      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data &&
          (events.length === 0 ? (
            <EmptyState
              icon="calendar"
              title={`Nothing in ${monthLabel(month)}`}
              subtitle="Add your public holidays and company events so the team can plan around them."
              tone={{ color: palette.coral, bg: tint.coral }}
              action={{ label: 'Add event', icon: 'plus', onPress: openNew }}
            />
          ) : (
            <View style={{ gap: 10 }}>
              <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
                {events.map((e, i) => {
                  const accent = accentFromKey(e.accentColorKey);
                  const { day, weekday } = dayParts(e.eventDate);
                  return (
                    <Pressable
                      key={e.id}
                      onPress={() => openEdit(e)}
                      style={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: 13,
                        paddingVertical: 13,
                        paddingHorizontal: 15,
                        borderTopWidth: i ? 1 : 0,
                        borderTopColor: palette.line,
                      }}
                    >
                      <View
                        style={{
                          width: 42,
                          height: 42,
                          borderRadius: radius.md,
                          backgroundColor: accent.bg,
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Text style={[font(800), { fontSize: 15, color: accent.color, fontVariant: ['tabular-nums'] }]}>
                          {day}
                        </Text>
                        <Text style={[font(600), { fontSize: 8.5, color: accent.color, marginTop: 1 }]}>{weekday}</Text>
                      </View>
                      <View style={{ flex: 1, minWidth: 0 }}>
                        <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                          {e.title}
                        </Text>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 5 }}>
                          <Chip
                            label={TYPE_LABEL[e.eventType]}
                            size="sm"
                            color={accent.color}
                            background={accent.bg}
                          />
                          {!!e.timeLabel && (
                            <Text style={[font(500), { fontSize: 11.5, color: palette.faint }]} numberOfLines={1}>
                              {e.timeLabel}
                            </Text>
                          )}
                        </View>
                      </View>
                      <Icon name="chevR" size={16} color={palette.faint} />
                    </Pressable>
                  );
                })}
              </Card>

              <Button label="Add event" icon="plus" variant="light" onPress={openNew} />
            </View>
          ))}
      </AsyncBoundary>
    </Screen>
  );
}
