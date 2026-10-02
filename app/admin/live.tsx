import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Card, Chip, EmptyState, LiveDot, SelectChips, StatTile, type SelectOption } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { SelfieThumb } from '@/components/attendance/SelfieThumb';
import { PhotoViewer } from '@/components/media/PhotoViewer';
import { useLiveAttendance } from '@/api/queries';
import { attendanceApi } from '@/api/endpoints';
import { accentFromKey } from '@/api/accents';
import { liveStatusLabel, matchesLiveFilter, type LiveFilter } from '@/lib/liveAttendance';
import { summaryLine } from '@/lib/employeeFields';
import { palette, font, tint } from '@/theme';

const FILTERS: SelectOption<LiveFilter>[] = [
  { value: 'all', label: 'All' },
  { value: 'in', label: 'In' },
  { value: 'remote', label: 'Remote' },
  { value: 'late', label: 'Late' },
  { value: 'out', label: 'Out' },
];

/** What the empty list says for each filter. */
const EMPTY_COPY: Record<LiveFilter, { title: string; subtitle: string }> = {
  all: { title: 'No active staff yet', subtitle: 'Add your team under Staff and they will appear here as they clock in.' },
  in: { title: 'Nobody clocked in yet', subtitle: 'People appear here as they check in for the day.' },
  remote: { title: 'Nobody working remotely', subtitle: 'Remote clock-ins for today will show up here.' },
  late: { title: 'Nobody is late', subtitle: 'Everyone who has clocked in today was on time.' },
  out: { title: 'Nobody is out', subtitle: 'No one is on leave or yet to clock in.' },
};

export default function Live() {
  const q = useLiveAttendance();
  const [filter, setFilter] = useState<LiveFilter>('all');
  // The selfie the admin is viewing enlarged (null → viewer closed).
  const [viewer, setViewer] = useState<{ recordId: string; name: string } | null>(null);

  const counts = q.data?.counts;
  const stat = (n: number | undefined) => (n == null ? '—' : String(n));
  const stats: { label: string; value: string; color: string }[] = [
    { label: 'In', value: stat(counts?.inOffice), color: palette.sage },
    { label: 'Remote', value: stat(counts?.remote), color: palette.violet },
    { label: 'Late', value: stat(counts?.late), color: palette.amber },
    { label: 'Out', value: stat(counts?.out), color: palette.coral },
  ];

  const rows = (q.data?.staff ?? []).filter((row) => matchesLiveFilter(row, filter));

  return (
    <CollapsingHeaderScreen
      back
      large
      title="Live Attendance"
      subtitle="Updates every 30s"
      accessory={<Chip label="LIVE" color={palette.sage} background={tint.sage} leading={<LiveDot color={palette.sage} />} />}
      headerExtra={
        <View style={{ marginTop: 16, gap: 14 }}>
          <View style={{ flexDirection: 'row', gap: 9 }}>
            {stats.map((s) => (
              <StatTile key={s.label} value={s.value} label={s.label} color={s.color} />
            ))}
          </View>
          <SelectChips options={FILTERS} value={filter} onChange={setFilter} />
        </View>
      }
    >
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data &&
          (rows.length === 0 ? (
            <EmptyState icon="pin" title={EMPTY_COPY[filter].title} subtitle={EMPTY_COPY[filter].subtitle} />
          ) : (
            <Card padding={0}>
              {rows.map((row, i) => {
                const accent = accentFromKey(row.accentColorKey);
                const recordId = row.hasPhoto ? row.attendanceRecordId : null;
                // Department + the real site they clocked in at — each only when there is one.
                const meta = summaryLine([row.department, row.location]);
                return (
                  <View
                    key={row.employeeId}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 12,
                      paddingVertical: 11,
                      paddingHorizontal: 16,
                      borderTopWidth: i ? 1 : 0,
                      borderTopColor: palette.line,
                    }}
                  >
                    <SelfieThumb
                      recordId={row.attendanceRecordId}
                      hasPhoto={row.hasPhoto}
                      initial={row.initial}
                      name={row.name}
                      tint={{ bg: accent.bg, fg: accent.color }}
                      size={38}
                      onPress={recordId ? () => setViewer({ recordId, name: row.name }) : undefined}
                    />
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 13, color: palette.ink }]} numberOfLines={1}>{row.name}</Text>
                      {meta && (
                        <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 5 }]} numberOfLines={1}>{meta}</Text>
                      )}
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Chip label={liveStatusLabel(row.status)} color={accent.color} background={accent.bg} size="sm" />
                      <Text style={[font(600), { fontSize: 10.5, color: palette.faint, marginTop: 5, fontVariant: ['tabular-nums'] }]}>{row.time}</Text>
                    </View>
                  </View>
                );
              })}
            </Card>
          ))}
      </AsyncBoundary>

      {/* enlarged clock-in selfie */}
      <PhotoViewer
        visible={!!viewer}
        onClose={() => setViewer(null)}
        uri={viewer ? attendanceApi.photoUrl(viewer.recordId) : null}
        authed
        title={viewer?.name}
        caption="Clock-in photo"
      />
    </CollapsingHeaderScreen>
  );
}
