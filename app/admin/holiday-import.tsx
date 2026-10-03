import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Chip, EmptyState, Icon, ScreenHeader, SelectChips, type SelectOption } from '@/components/ui';
import { useHolidaySuggestions, useImportHolidays } from '@/api/queries';
import type { HolidaySuggestion } from '@/api/types';
import { alertError } from '@/lib/errors';
import { palette, font, radius, spacing, tint } from '@/theme';

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2027-02-06" → "Sat, 6 Feb" (the raw value if it can't parse). */
function dateLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return iso;
  const date = new Date(y, m - 1, d);
  return `${WEEKDAYS[date.getDay()]}, ${d} ${MONTHS[m - 1]}`;
}

/** From October on, admins are usually planning next year. */
function preferredYear(now: Date = new Date()): number {
  return now.getMonth() >= 9 ? now.getFullYear() + 1 : now.getFullYear();
}

/** Ticked by default: nationwide holidays not yet on the calendar. Regional ones are the admin's call. */
function defaultTicks(items: HolidaySuggestion[]): string[] {
  return items.filter((h) => !h.alreadyAdded && !h.note).map((h) => h.date);
}

/** Tick box for a suggestion row. */
function CheckBox({ checked, disabled }: { checked: boolean; disabled: boolean }) {
  const on = checked && !disabled;
  return (
    <View
      style={{
        width: 22,
        height: 22,
        borderRadius: radius.sm - 2,
        borderWidth: on ? 0 : 1.5,
        borderColor: palette.line,
        backgroundColor: on ? palette.coral : disabled ? palette.surfaceSunken : palette.surface,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {on && <Icon name="check" size={14} color={palette.white} stroke={2.6} />}
    </View>
  );
}

/**
 * Review a suggested list of Malaysian public holidays and add the ones that
 * apply. Dates move every year and differ by state, and the list comes from
 * published listings (not the gazette), so nothing is added automatically —
 * the admin ticks what their company observes.
 */
export default function HolidayImport() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const currentYear = new Date().getFullYear();
  const preferred = preferredYear();

  // null until the admin picks a chip; then the first response settles the default.
  const [picked, setPicked] = useState<number | null>(null);
  const year = picked ?? preferred;
  const q = useHolidaySuggestions(year);
  const importHolidays = useImportHolidays();

  // Ticks per year, seeded with the defaults the first time a year loads.
  const [ticks, setTicks] = useState<Record<number, string[]>>({});

  const years = useMemo(() => q.data?.years ?? [], [q.data]);
  const items = useMemo(() => q.data?.items ?? [], [q.data]);

  // Settle the default year: next year (Oct–Dec) only when the catalogue has it.
  useEffect(() => {
    if (picked != null || !q.data) return;
    if (years.length > 0 && !years.includes(preferred)) setPicked(currentYear);
    else setPicked(preferred);
  }, [picked, q.data, years, preferred, currentYear]);

  useEffect(() => {
    if (!q.data || (q.data.year ?? year) !== year || ticks[year]) return;
    setTicks((t) => ({ ...t, [year]: defaultTicks(items) }));
  }, [q.data, items, year, ticks]);

  const selected = new Set(ticks[year] ?? []);
  const chosen = items.filter((h) => !h.alreadyAdded && selected.has(h.date));
  const count = chosen.length;

  const toggle = (date: string) =>
    setTicks((t) => {
      const cur = new Set(t[year] ?? []);
      if (cur.has(date)) cur.delete(date);
      else cur.add(date);
      return { ...t, [year]: [...cur] };
    });

  const yearOptions: SelectOption<string>[] = (years.length ? years : [year]).map((y) => ({
    value: String(y),
    label: String(y),
  }));

  const submit = () => {
    if (count === 0) return;
    importHolidays.mutate(
      chosen.map((h) => ({ date: h.date, name: h.name })),
      {
        onSuccess: (res) => {
          const created = res?.created ?? count;
          const skipped = res?.skipped ?? 0;
          const title = `Added ${created} ${created === 1 ? 'holiday' : 'holidays'}`;
          const body =
            skipped > 0
              ? `${skipped} ${skipped === 1 ? 'was' : 'were'} already on your calendar, so we skipped ${skipped === 1 ? 'it' : 'them'}.`
              : 'Everyone will see them on their calendar.';
          Alert.alert(title, body, [{ text: 'OK', onPress: () => router.back() }]);
        },
        onError: (e) => alertError("Couldn't add holidays", e),
      },
    );
  };

  const addable = items.filter((h) => !h.alreadyAdded).length;

  return (
    <Screen scroll={false} paddingX={0}>
      <View style={{ paddingHorizontal: spacing.screenX }}>
        <ScreenHeader back title="Public holidays" />
        <Text style={[font(500), { fontSize: 12, lineHeight: 18, color: palette.faint, marginTop: 4, marginBottom: 14 }]}>
          Dates move every year and some holidays differ by state. Tick the ones your company observes —
          holidays are paid in payroll.
        </Text>
        {yearOptions.length > 0 && (
          <View style={{ marginBottom: 14 }}>
            <SelectChips
              options={yearOptions}
              value={String(year)}
              onChange={(v) => setPicked(Number(v))}
            />
          </View>
        )}
      </View>

      <ScrollView
        style={{ flex: 1 }}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: spacing.screenX, paddingBottom: spacing.lg }}
      >
        <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
          {q.data &&
            (items.length === 0 ? (
              <EmptyState
                icon="calendar"
                title={`No suggestions for ${year} yet`}
                subtitle="We don't have a public holiday list for this year. You can still add holidays one by one."
                tone={{ color: palette.coral, bg: tint.coral }}
              />
            ) : (
              <>
                <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
                  {items.map((h, i) => {
                    const checked = h.alreadyAdded || selected.has(h.date);
                    return (
                      <Pressable
                        key={h.date}
                        onPress={() => toggle(h.date)}
                        disabled={h.alreadyAdded}
                        accessibilityRole="checkbox"
                        accessibilityLabel={`${h.name}, ${dateLabel(h.date)}`}
                        accessibilityState={{ checked, disabled: h.alreadyAdded }}
                        style={({ pressed }) => ({
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 13,
                          paddingVertical: 13,
                          paddingHorizontal: 15,
                          borderTopWidth: i ? 1 : 0,
                          borderTopColor: palette.line,
                          backgroundColor: pressed ? palette.surfaceSunken : palette.surface,
                        })}
                      >
                        <CheckBox checked={checked} disabled={h.alreadyAdded} />
                        <View style={{ flex: 1, minWidth: 0, opacity: h.alreadyAdded ? 0.6 : 1 }}>
                          <Text style={[font(700), { fontSize: 13.5, lineHeight: 18, color: palette.ink }]}>{h.name}</Text>
                          <Text
                            style={[
                              font(600),
                              { fontSize: 11.5, lineHeight: 16, color: palette.soft, marginTop: 2, fontVariant: ['tabular-nums'] },
                            ]}
                          >
                            {dateLabel(h.date)}
                          </Text>
                          {!!h.note && (
                            <Text style={[font(500), { fontSize: 11, lineHeight: 15, color: palette.faint, marginTop: 3 }]}>
                              {h.note}
                            </Text>
                          )}
                        </View>
                        {h.alreadyAdded && <Chip label="Added" size="sm" color={palette.sage} background={tint.sage} />}
                      </Pressable>
                    );
                  })}
                </Card>
                {addable === 0 && (
                  <Text style={[font(600), { fontSize: 12, lineHeight: 17, color: palette.soft, marginTop: 12, textAlign: 'center' }]}>
                    All of {year}'s holidays are already on your calendar.
                  </Text>
                )}
              </>
            ))}
        </AsyncBoundary>
      </ScrollView>

      {/* Pinned footer: provenance, then the one action. */}
      <View
        style={{
          paddingHorizontal: spacing.screenX,
          paddingTop: spacing.md,
          paddingBottom: insets.bottom,
          borderTopWidth: 1,
          borderTopColor: palette.line,
          backgroundColor: palette.bg,
        }}
      >
        {!!q.data?.source && (
          <Text style={[font(500), { fontSize: 11, lineHeight: 16, color: palette.faint, marginBottom: spacing.md }]}>
            {q.data.source}
          </Text>
        )}
        <Button
          label={
            importHolidays.isPending
              ? 'Adding…'
              : count === 0
                ? 'Add holidays'
                : `Add ${count} ${count === 1 ? 'holiday' : 'holidays'}`
          }
          icon="plus"
          disabled={count === 0 || importHolidays.isPending}
          onPress={submit}
        />
      </View>
    </Screen>
  );
}
