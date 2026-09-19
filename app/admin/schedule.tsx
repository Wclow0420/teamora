import React, { useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Avatar, Card, Chip, EmptyState, Icon } from '@/components/ui';
import { useSchedule } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import type { DayShifts } from '@/api/types';
import { palette, font, radius, gradients, tint, shadows } from '@/theme';

export default function Schedule() {
  const router = useRouter();
  const q = useSchedule();
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const days: DayShifts[] = q.data?.days ?? [];
  const selected: DayShifts | undefined = selectedDate
    ? days.find((d) => d.date === selectedDate)
    : days.find((d) => d.shifts.length > 0) ?? days[0];

  return (
    <CollapsingHeaderScreen
      back
      large
      title="Scheduling"
      subtitle={q.data ? `Week of ${q.data.weekLabel}` : 'Scheduling'}
      accessory={
        <Pressable onPress={() => router.push('/admin/shift-assign')} hitSlop={6}>
          <LinearGradient colors={gradients.coral} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={[{ width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, shadows.coral]}>
            <Icon name="plus" size={22} color={palette.white} />
          </LinearGradient>
        </Pressable>
      }
      headerExtra={
        <View style={{ marginTop: 14 }}>
          {/* day selector */}
          <View style={{ flexDirection: 'row', gap: 7, marginBottom: 16 }}>
            {days.map((d) => {
              const active = selected?.date === d.date;
              const inner = (
                <>
                  <Text style={[font(700), { fontSize: 9.5, color: active ? palette.white : palette.soft, opacity: active ? 0.9 : 1 }]}>{d.weekdayLabel}</Text>
                  <Text style={[font(800), { fontSize: 15, color: active ? palette.white : palette.soft, marginTop: 6 }]}>{d.dayLabel}</Text>
                </>
              );
              return active ? (
                <LinearGradient key={d.date} colors={gradients.coral} start={{ x: 0, y: 0 }} end={{ x: 0.2, y: 1 }} style={{ flex: 1, paddingVertical: 10, borderRadius: 13, alignItems: 'center' }}>
                  {inner}
                </LinearGradient>
              ) : (
                <Pressable
                  key={d.date}
                  onPress={() => setSelectedDate(d.date)}
                  style={{ flex: 1, paddingVertical: 10, borderRadius: 13, alignItems: 'center', backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.line }}
                >
                  {inner}
                </Pressable>
              );
            })}
          </View>

          {/* sub-row */}
          {selected && (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginHorizontal: 2, marginBottom: 12 }}>
              <Text style={[font(700), { fontSize: 14, color: palette.ink }]}>{selected.weekdayLabel}, {selected.dayLabel}</Text>
              <Chip label={`${selected.onShift} on shift`} color={palette.faint} background="transparent" />
            </View>
          )}
        </View>
      }
    >
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {selected && (selected.shifts.length === 0 ? (
          <EmptyState
            icon="calendar"
            title={`No shifts for ${selected.weekdayLabel}`}
            subtitle="Nobody is scheduled this day yet."
            action={{ label: 'Assign shift', icon: 'plus', onPress: () => router.push('/admin/shift-assign') }}
          />
        ) : (
          <View style={{ gap: 9 }}>
            {selected.shifts.map((s) => {
              const accent = accentFromKey(s.shiftColorKey);
              return (
                <Card key={s.employeeId} padding={0} elevated={false} style={{ borderRadius: radius.lg, borderLeftWidth: 3, borderLeftColor: accent.color }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, paddingHorizontal: 14 }}>
                    <Avatar initial={s.initial} size={40} tint={{ bg: tint.neutral, fg: palette.soft }} />
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 13, color: palette.ink }]} numberOfLines={1}>{s.employeeName}</Text>
                      <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 5 }]}>{s.department}</Text>
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Chip label={s.shiftLabel} background={accent.bg} color={accent.color} />
                      <Text style={[font(600), { fontSize: 10.5, color: palette.faint, marginTop: 6 }]}>{s.timeLabel}</Text>
                    </View>
                  </View>
                </Card>
              );
            })}
          </View>
        ))}
      </AsyncBoundary>
    </CollapsingHeaderScreen>
  );
}
