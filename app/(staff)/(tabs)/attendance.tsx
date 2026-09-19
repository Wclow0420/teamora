import React from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { BarChart, Button, Card, Chip, EmptyState, Icon, StatTile } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useAttendanceHistory } from '@/api/queries';
import { statusAccent } from '@/api/accents';
import { palette, font, tint } from '@/theme';

/** "2026-06" → "June 2026"; falls back to the raw value if it can't parse. */
function formatMonth(month: string | undefined): string {
  if (!month) return '—';
  const [y, m] = month.split('-').map(Number);
  if (!y || !m) return month;
  return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

export default function Attendance() {
  const router = useRouter();
  const q = useAttendanceHistory();

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      title="Attendance"
      accessory={<Chip label={formatMonth(q.data?.month)} background={palette.surface} color={palette.soft} leading={<Icon name="chevD" size={14} color={palette.soft} />} />}
    >
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && (
          <>
            {/* stats */}
            <View style={{ flexDirection: 'row', gap: 9 }}>
              <StatTile value={String(q.data.present)} label="Present" color={palette.sage} />
              <StatTile value={String(q.data.late)} label="Late" color={palette.amber} />
              <StatTile value={String(q.data.leave)} label="Leave" color={palette.coral} />
              <StatTile value={q.data.otHoursLabel} label="OT" color={palette.violet} />
            </View>

            {/* week chart */}
            <Card style={{ marginTop: 14 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>This week</Text>
                <Text style={[font(600), { fontSize: 12, color: palette.faint }]}>{q.data.weeklyTotalLabel} total</Text>
              </View>
              <BarChart
                data={q.data.weeklyHours}
                max={9}
                labels={['M', 'T', 'W', 'T', 'F', 'S', 'S']}
                highlight={q.data.weeklyHours.map((_, i) => i).filter((i) => q.data!.weeklyHours[i] !== 0)}
                empty={q.data.weeklyHours.map((_, i) => i).filter((i) => q.data!.weeklyHours[i] === 0)}
                barColor="#EC6A4D"
              />
            </Card>

            {/* day list */}
            {q.data.days.length === 0 ? (
              <View style={{ marginTop: 14 }}>
                <EmptyState
                  icon="clock"
                  title="No attendance yet"
                  subtitle="Your clock-in history for this month will appear here."
                  tone={{ color: palette.sage, bg: tint.sage }}
                />
              </View>
            ) : (
            <Card padding={0} style={{ marginTop: 14, paddingHorizontal: 16 }}>
              {q.data.days.map((d, i) => {
                const accent = statusAccent(d.status);
                return (
                  <View
                    key={d.date}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 12,
                      paddingVertical: 13,
                      borderTopWidth: i ? 1 : 0,
                      borderTopColor: palette.line,
                    }}
                  >
                    <View style={{ width: 42, alignItems: 'center' }}>
                      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>{d.date}</Text>
                      <Text style={[font(600), { fontSize: 10, color: palette.faint, marginTop: 4 }]}>{d.weekday}</Text>
                    </View>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Chip label={d.statusLabel} color={accent.color} background={accent.bg} size="sm" dot={d.live} />
                      <Text style={[font(600), { fontSize: 11.5, color: palette.faint, marginTop: 7 }]}>
                        {d.clockIn ?? '—'}
                        {d.clockOut ? ` → ${d.clockOut}` : ''}
                      </Text>
                    </View>
                    <Text style={[font(700), { fontSize: 13, color: d.workedLabel === 'Leave' ? palette.faint : palette.ink }]}>{d.workedLabel}</Text>
                  </View>
                );
              })}
            </Card>
            )}

            <Button label="Log overtime" icon="clock" variant="light" style={{ marginTop: 14 }} onPress={() => router.push('/overtime-submit')} />
          </>
        )}
      </AsyncBoundary>
    </CollapsingHeaderScreen>
  );
}
