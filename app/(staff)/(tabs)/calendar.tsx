import React, { useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Card, Icon, IconTile, type IconName } from '@/components/ui';
import { useCalendar } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import { palette, font, radius, gradients } from '@/theme';

const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const LEGEND: [string, string][] = [
  ['Leave', palette.coral],
  ['Holiday', palette.amber],
  ['Town hall', palette.sage],
  ['Event', palette.violet],
];

export default function Calendar() {
  const now = new Date();
  const [ym, setYm] = useState({ year: now.getFullYear(), month: now.getMonth() }); // month 0-indexed
  const monthParam = `${ym.year}-${String(ym.month + 1).padStart(2, '0')}`;
  const q = useCalendar(monthParam);

  const firstWeekday = new Date(ym.year, ym.month, 1).getDay(); // 0=Sun
  const daysInMonth = new Date(ym.year, ym.month + 1, 0).getDate();
  const todayDom = now.getFullYear() === ym.year && now.getMonth() === ym.month ? now.getDate() : null;
  const cells: (number | null)[] = [...Array<null>(firstWeekday).fill(null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];

  const shift = (delta: number) => {
    setYm((p) => {
      const m = p.month + delta;
      return { year: p.year + Math.floor(m / 12), month: ((m % 12) + 12) % 12 };
    });
  };

  return (
    <CollapsingHeaderScreen bottomInset={70} title="Calendar">
      {/* month card */}
      <Card padding={0} style={{ paddingHorizontal: 14, paddingTop: 16, paddingBottom: 12, borderRadius: 22 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 4, paddingBottom: 12 }}>
          <Pressable onPress={() => shift(-1)} hitSlop={10}>
            <Icon name="chevL" size={18} color={palette.faint} />
          </Pressable>
          <Text style={[font(700), { fontSize: 14, color: palette.ink }]}>
            {q.data?.monthLabel ?? `${ym.year}-${ym.month + 1}`}
          </Text>
          <Pressable onPress={() => shift(1)} hitSlop={10}>
            <Icon name="chevR" size={18} color={palette.faint} />
          </Pressable>
        </View>

        {/* weekday header */}
        <View style={{ flexDirection: 'row', marginBottom: 6 }}>
          {WEEKDAYS.map((d, i) => (
            <View key={i} style={{ flex: 1, alignItems: 'center', paddingVertical: 2 }}>
              <Text style={[font(700), { fontSize: 10, color: palette.faint }]}>{d}</Text>
            </View>
          ))}
        </View>

        {/* grid */}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
          {cells.map((n, i) => {
            if (!n) return <View key={i} style={{ width: `${100 / 7}%`, aspectRatio: 1 }} />;
            const isToday = n === todayDom;
            const dots = (q.data?.events?.[String(n)] ?? []).map((k) => accentFromKey(k).color);
            const cell = (
              <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3 }}>
                <Text style={[font(isToday ? 700 : 600), { fontSize: 12.5, color: isToday ? palette.white : palette.ink }]}>{n}</Text>
                <View style={{ flexDirection: 'row', gap: 2, height: 4 }}>
                  {dots.slice(0, 3).map((c, j) => (
                    <View key={j} style={{ width: 4, height: 4, borderRadius: radius.pill, backgroundColor: isToday ? palette.white : c }} />
                  ))}
                </View>
              </View>
            );
            return (
              <View key={i} style={{ width: `${100 / 7}%`, aspectRatio: 1, padding: 1 }}>
                {isToday ? (
                  <LinearGradient colors={gradients.coral} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ flex: 1, borderRadius: 11, alignItems: 'center', justifyContent: 'center' }}>
                    {cell}
                  </LinearGradient>
                ) : (
                  cell
                )}
              </View>
            );
          })}
        </View>

        {/* legend */}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, paddingHorizontal: 4, paddingTop: 12, marginTop: 8, borderTopWidth: 1, borderTopColor: palette.line }}>
          {LEGEND.map(([l, c]) => (
            <View key={l} style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
              <View style={{ width: 7, height: 7, borderRadius: radius.pill, backgroundColor: c }} />
              <Text style={[font(600), { fontSize: 10.5, color: palette.soft }]}>{l}</Text>
            </View>
          ))}
        </View>
      </Card>

      {/* upcoming */}
      <Text style={[font(700), { fontSize: 14, color: palette.ink, marginTop: 16, marginBottom: 11, marginHorizontal: 2 }]}>Upcoming</Text>
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch} minHeight={80}>
        {q.data &&
          (q.data.upcoming.length === 0 ? (
            <Text style={[font(500), { fontSize: 12.5, color: palette.faint, marginHorizontal: 2 }]}>Nothing coming up.</Text>
          ) : (
            <View style={{ gap: 9 }}>
              {q.data.upcoming.map((u, i) => {
                const accent = accentFromKey(u.accentColorKey);
                return (
                  <Card key={i} padding={0} style={{ paddingHorizontal: 13, paddingVertical: 12, borderRadius: radius.lg, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                    <IconTile icon={u.iconName as IconName} color={accent.color} background={accent.bg} size={38} iconSize={19} cornerRadius={11} />
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 12.5, color: palette.ink, lineHeight: 16 }]}>{u.title}</Text>
                      <Text style={[font(600), { fontSize: 11, color: palette.faint, marginTop: 5 }]}>{u.dateLabel}</Text>
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
