import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { Card, CheckBox, Chip, EmptyState } from '@/components/ui';
import type { HolidaySuggestion } from '@/api/types';
import { holidayDateLabel } from '@/lib/holidays';
import { palette, radius, tint, type } from '@/theme';

export type HolidayGroup = { title: string; items: HolidaySuggestion[] };

type Props = {
  groups: HolidayGroup[];
  /** Ticked dates (ISO). */
  ticks: Set<string>;
  onToggle: (date: string) => void;
  /** Provenance line from the server's catalogue. */
  source?: string;
};

/**
 * "Public holidays" — a compact tick list of the suggested dates still ahead.
 * Nationwide holidays start ticked; state-specific ones start unticked with
 * their note. Nothing is added until the admin continues (holidays affect pay).
 */
export function HolidaysStep({ groups, ticks, onToggle, source }: Props) {
  const shown = groups.filter((g) => g.items.length > 0);
  if (shown.length === 0) {
    return (
      <EmptyState
        icon="calendar"
        title="No suggestions right now"
        subtitle="We don't have a public holiday list for the coming months yet. You can add holidays one by one from Company calendar."
        tone={{ color: palette.coral, bg: tint.coral }}
      />
    );
  }
  return (
    <View style={{ gap: 18 }}>
      {shown.map((group) => (
        <View key={group.title}>
          {shown.length > 1 && <Text style={[type.eyebrow, { color: palette.faint, marginBottom: 9 }]}>{group.title}</Text>}
          <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
            {group.items.map((h, i) => {
              const checked = h.alreadyAdded || ticks.has(h.date);
              return (
                <Pressable
                  key={h.date}
                  onPress={() => onToggle(h.date)}
                  disabled={h.alreadyAdded}
                  accessibilityRole="checkbox"
                  accessibilityLabel={`${h.name}, ${holidayDateLabel(h.date)}${h.note ? `. ${h.note}` : ''}`}
                  accessibilityState={{ checked, disabled: h.alreadyAdded }}
                  style={({ pressed }) => ({
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    paddingVertical: 11,
                    paddingHorizontal: 14,
                    borderTopWidth: i ? 1 : 0,
                    borderTopColor: palette.line,
                    backgroundColor: pressed ? palette.surfaceSunken : palette.surface,
                  })}
                >
                  <CheckBox checked={checked} disabled={h.alreadyAdded} />
                  <View style={{ flex: 1, minWidth: 0, opacity: h.alreadyAdded ? 0.6 : 1 }}>
                    <Text style={[type.bodyStrong, { fontSize: 13.5, lineHeight: 18, color: palette.ink }]}>{h.name}</Text>
                    {!!h.note && (
                      <Text style={[type.meta, { fontSize: 11.5, lineHeight: 15, color: palette.faint, marginTop: 2 }]}>{h.note}</Text>
                    )}
                  </View>
                  {h.alreadyAdded ? (
                    <Chip label="Added" size="sm" color={palette.sage} background={tint.sage} style={{ alignSelf: 'center' }} />
                  ) : (
                    <Text style={[type.meta, { color: palette.soft, fontVariant: ['tabular-nums'] }]}>{holidayDateLabel(h.date)}</Text>
                  )}
                </Pressable>
              );
            })}
          </Card>
        </View>
      ))}
      <Text style={[type.meta, { lineHeight: 18, color: palette.faint }]}>
        Holidays are paid days in payroll, so only the ones you tick are added. Some differ by state — check the
        unticked ones.{source ? ` ${source}` : ''}
      </Text>
    </View>
  );
}
