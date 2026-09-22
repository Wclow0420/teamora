import React from 'react';
import { View, Text } from 'react-native';
import { Card, SelectChips, TextField, WeekdayToggles, type SelectOption } from '@/components/ui';
import type { PayBasis } from '@/api/types';
import { deriveRates, describeMask } from '@/lib/workweek';
import { palette, font, radius, tint } from '@/theme';

const PAY_BASIS_OPTIONS: SelectOption<PayBasis>[] = [
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'HOURLY', label: 'Hourly' },
];

/** Format a number → "#,##0.00". */
function money(v: number): string {
  const [w, c] = Math.abs(v).toFixed(2).split('.');
  return `${w.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${c}`;
}

type Props = {
  /** Parsed monthly salary (RM), or null when blank/invalid — drives the preview. */
  monthlySalary: number | null;
  payBasis: PayBasis;
  onPayBasis: (v: PayBasis) => void;
  /** Working-weekday bitmask. */
  workingDays: number;
  onWorkingDays: (mask: number) => void;
  /** Hours per day as raw input text. */
  hoursPerDay: string;
  onHoursPerDay: (text: string) => void;
};

/**
 * "Compensation" form section: pay basis, working-day schedule and hours/day,
 * plus a live derived daily/hourly rate preview for the current month. Shared by
 * the add- and edit-employee screens.
 */
export function CompensationFields({
  monthlySalary,
  payBasis,
  onPayBasis,
  workingDays,
  onWorkingDays,
  hoursPerDay,
  onHoursPerDay,
}: Props) {
  const hours = Number(hoursPerDay);
  const validHours = Number.isFinite(hours) && hours > 0 ? hours : 8;
  const now = new Date();
  const { scheduledDays, daily, hourly } = deriveRates(
    monthlySalary,
    workingDays,
    validHours,
    now.getFullYear(),
    now.getMonth(),
  );

  return (
    <View style={{ gap: 14, marginTop: 4 }}>
      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Compensation</Text>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
        Daily and hourly rates derive from the monthly salary and the working-day schedule.
      </Text>

      <SelectChips label="Pay basis" options={PAY_BASIS_OPTIONS} value={payBasis} onChange={onPayBasis} />
      <WeekdayToggles label="Working days" value={workingDays} onChange={onWorkingDays} />
      <TextField
        label="Hours per day"
        value={hoursPerDay}
        onChangeText={onHoursPerDay}
        keyboardType="decimal-pad"
        placeholder="e.g. 8"
      />

      {/* live derived preview */}
      <Card padding={0} elevated={false} style={{ borderRadius: radius.lg, padding: 14, backgroundColor: tint.sage }}>
        <Text style={[font(700), { fontSize: 11, letterSpacing: 0.8, textTransform: 'uppercase', color: palette.sage }]}>
          Derived rates · this month
        </Text>
        {daily == null ? (
          <Text style={[font(500), { fontSize: 12.5, color: palette.soft, marginTop: 8, lineHeight: 18 }]}>
            Enter a monthly salary to preview the daily and hourly rates.
          </Text>
        ) : (
          <View style={{ flexDirection: 'row', gap: 20, marginTop: 10 }}>
            <View>
              <Text style={[font(800), { fontSize: 18, color: palette.ink, fontVariant: ['tabular-nums'] }]}>
                RM {money(daily)}
              </Text>
              <Text style={[font(500), { fontSize: 11, color: palette.soft, marginTop: 3 }]}>per day</Text>
            </View>
            <View>
              <Text style={[font(800), { fontSize: 18, color: palette.ink, fontVariant: ['tabular-nums'] }]}>
                {hourly == null ? '—' : `RM ${money(hourly)}`}
              </Text>
              <Text style={[font(500), { fontSize: 11, color: palette.soft, marginTop: 3 }]}>per hour</Text>
            </View>
          </View>
        )}
        <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 10, lineHeight: 15 }]}>
          {describeMask(workingDays)} · {scheduledDays} working days this month
        </Text>
      </Card>
    </View>
  );
}
