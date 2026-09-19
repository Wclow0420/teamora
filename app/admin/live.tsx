import React from 'react';
import { View, Text } from 'react-native';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Card, Chip, EmptyState, Icon, LiveDot, Placeholder, StatTile } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useLiveAttendance } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import { palette, font, radius, tint } from '@/theme';

const PINS: { x: `${number}%`; y: `${number}%`; color: string }[] = [
  { x: '30%', y: '40%', color: palette.sage },
  { x: '58%', y: '55%', color: palette.violet },
  { x: '70%', y: '35%', color: palette.amber },
  { x: '44%', y: '68%', color: palette.sage },
];

const FILTERS = ['All', 'In office', 'Remote', 'Late'];

export default function Live() {
  const q = useLiveAttendance();
  const counts = q.data?.counts ?? { inOffice: 0, remote: 0, late: 0, out: 0 };

  const stats: { label: string; value: string; color: string }[] = [
    { label: 'In', value: String(counts.inOffice), color: palette.sage },
    { label: 'Remote', value: String(counts.remote), color: palette.violet },
    { label: 'Late', value: String(counts.late), color: palette.amber },
    { label: 'Out', value: String(counts.out), color: palette.coral },
  ];

  return (
    <CollapsingHeaderScreen
      back
      large
      title="Live Attendance"
      subtitle="Updates every 30s"
      accessory={<Chip label="LIVE" color={palette.sage} background={tint.sage} leading={<LiveDot color={palette.sage} />} />}
      headerExtra={
        <View style={{ marginTop: 16 }}>
      {/* stat row */}
      <View style={{ flexDirection: 'row', gap: 9 }}>
        {stats.map((s) => (
          <StatTile key={s.label} value={s.value} label={s.label} color={s.color} />
        ))}
      </View>

      {/* mini map */}
      <View style={{ marginTop: 13, height: 120, borderRadius: radius.xl, overflow: 'hidden', borderWidth: 1, borderColor: palette.line }}>
        <Placeholder label="office floor map" radius={0} tintColor="rgba(236,106,77,0.07)" />
        <View
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: 84,
            height: 84,
            marginLeft: -42,
            marginTop: -42,
            borderRadius: radius.pill,
            borderWidth: 2,
            borderColor: palette.sage,
            borderStyle: 'dashed',
          }}
        />
        {PINS.map((p, i) => (
          <View key={i} style={{ position: 'absolute', left: p.x, top: p.y, marginLeft: -10, marginTop: -20 }}>
            <Icon name="pin" size={20} stroke={2.3} color={p.color} />
          </View>
        ))}
        <View
          style={{
            position: 'absolute',
            left: 12,
            top: 12,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 6,
            backgroundColor: palette.white,
            borderRadius: 9,
            paddingVertical: 6,
            paddingHorizontal: 10,
            borderWidth: 1,
            borderColor: palette.line,
          }}
        >
          <Icon name="building" size={13} color={palette.coral} />
          <Text style={[font(700), { fontSize: 11, color: palette.ink }]}>Bangsar South HQ</Text>
        </View>
      </View>

      {/* filters */}
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 14, marginBottom: 12 }}>
        {FILTERS.map((f, i) => {
          const active = i === 0;
          return (
            <Chip
              key={f}
              label={f}
              color={active ? palette.white : palette.soft}
              background={active ? palette.ink : palette.surface}
              style={
                active
                  ? { paddingVertical: 8, paddingHorizontal: 14 }
                  : { paddingVertical: 8, paddingHorizontal: 14, borderWidth: 1, borderColor: palette.line }
              }
            />
          );
        })}
      </View>
        </View>
      }
    >
      {/* staff list */}
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && (q.data.staff.length === 0 ? (
          <EmptyState
            icon="pin"
            title="Nobody clocked in yet"
            subtitle="Live attendance appears here as your team checks in for the day."
          />
        ) : (
          <Card padding={0}>
            {q.data.staff.map((row, i) => {
              const accent = accentFromKey(row.accentColorKey);
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
                  <Avatar initial={row.initial} size={38} tint={{ bg: accent.bg, fg: accent.color }} />
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13, color: palette.ink }]} numberOfLines={1}>{row.name}</Text>
                    <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 5 }]} numberOfLines={1}>{row.department}</Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Chip label={row.status} color={accent.color} background={accent.bg} size="sm" />
                    <Text style={[font(600), { fontSize: 10.5, color: palette.faint, marginTop: 5 }]}>{row.time}</Text>
                  </View>
                </View>
              );
            })}
          </Card>
        ))}
      </AsyncBoundary>
    </CollapsingHeaderScreen>
  );
}
