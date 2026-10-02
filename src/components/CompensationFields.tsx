import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { Card, Icon, SelectChips, TextField, WeekdayToggles, type SelectOption } from '@/components/ui';
import type { PayBasis } from '@/api/types';
import { NO_WORKING_DAYS_ERROR, deriveRates, describeMask, isEmptyMask } from '@/lib/workweek';
import { palette, font, radius, spacing, tint } from '@/theme';

/** A pay-basis choice: an explicit basis, or "follow the company default". */
export type PayBasisChoice = PayBasis | 'DEFAULT';

const PAY_BASIS_LABEL: Record<PayBasis, string> = { MONTHLY: 'Monthly', DAILY: 'Daily', HOURLY: 'Hourly' };

/** The company-wide pay settings an employee falls back to. */
export type CompensationDefaults = { payBasis: PayBasis; workingDays: number; hoursPerDay: number };

/** Format a number → "#,##0.00". */
function money(v: number): string {
  const [w, c] = Math.abs(v).toFixed(2).split('.');
  return `${w.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${c}`;
}

type Props = {
  /** Parsed monthly salary (RM), or null when blank/invalid — drives the preview. */
  monthlySalary: number | null;
  payBasis: PayBasisChoice;
  onPayBasis: (v: PayBasisChoice) => void;
  /** Working-weekday bitmask (the employee's own schedule; ignored while following the default). */
  workingDays: number;
  onWorkingDays: (mask: number) => void;
  /** Follow the company's working days instead of an own schedule. */
  useDefaultWorkingDays: boolean;
  onUseDefaultWorkingDays: (v: boolean) => void;
  /** Hours per day as raw input text. Blank = follow the company default. */
  hoursPerDay: string;
  onHoursPerDay: (text: string) => void;
  /** Company defaults, once loaded — named in the "company default" options. */
  defaults?: CompensationDefaults | null;
  /** The values in force when a field follows the default (the preview's fallback). */
  fallback: CompensationDefaults;
};

/**
 * "Pay basis & schedule" form section: pay basis, working-day schedule and hours/day —
 * each either the employee's own value or "use the company default" — plus a
 * live derived daily/hourly rate preview for the current month.
 */
export function CompensationFields({
  monthlySalary,
  payBasis,
  onPayBasis,
  workingDays,
  onWorkingDays,
  useDefaultWorkingDays,
  onUseDefaultWorkingDays,
  hoursPerDay,
  onHoursPerDay,
  defaults,
  fallback,
}: Props) {
  const inForce = defaults ?? fallback;
  const effectiveDays = useDefaultWorkingDays ? inForce.workingDays : workingDays;
  const hours = Number(hoursPerDay);
  const effectiveHours = Number.isFinite(hours) && hours > 0 ? hours : inForce.hoursPerDay;
  const now = new Date();
  const { scheduledDays, daily, hourly } = deriveRates(
    monthlySalary,
    effectiveDays,
    effectiveHours,
    now.getFullYear(),
    now.getMonth(),
  );

  const payBasisOptions: SelectOption<PayBasisChoice>[] = [
    { value: 'MONTHLY', label: PAY_BASIS_LABEL.MONTHLY },
    { value: 'DAILY', label: PAY_BASIS_LABEL.DAILY },
    { value: 'HOURLY', label: PAY_BASIS_LABEL.HOURLY },
    {
      value: 'DEFAULT',
      label: defaults ? `Company default (${PAY_BASIS_LABEL[defaults.payBasis]})` : 'Company default',
    },
  ];

  return (
    <View style={{ gap: 14, marginTop: 4 }}>
      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Pay basis & schedule</Text>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
        Daily and hourly rates derive from the monthly salary and the working-day schedule.
      </Text>

      <SelectChips label="Pay basis" options={payBasisOptions} value={payBasis} onChange={onPayBasis} />

      <View style={{ gap: spacing.sm }}>
        <WeekdayToggles
          label="Working days"
          value={effectiveDays}
          onChange={onWorkingDays}
          // Following the company default: its days are shown, dimmed and locked.
          // Untick the box below to give this person their own schedule.
          disabled={useDefaultWorkingDays}
          error={!useDefaultWorkingDays && isEmptyMask(workingDays) ? NO_WORKING_DAYS_ERROR : null}
        />
        <Pressable
          onPress={() => onUseDefaultWorkingDays(!useDefaultWorkingDays)}
          accessibilityRole="checkbox"
          accessibilityLabel="Use company default working days"
          accessibilityState={{ checked: useDefaultWorkingDays }}
          hitSlop={8}
          style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, alignSelf: 'flex-start' }}
        >
          <View
            style={{
              width: 20,
              height: 20,
              borderRadius: radius.sm,
              borderWidth: 1.5,
              borderColor: useDefaultWorkingDays ? palette.coral : palette.inactive,
              backgroundColor: useDefaultWorkingDays ? palette.coral : palette.surface,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {useDefaultWorkingDays && <Icon name="check" size={13} color={palette.white} stroke={2.6} />}
          </View>
          <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>
            Use company default{defaults ? ` (${describeMask(defaults.workingDays)})` : ''}
          </Text>
        </Pressable>
      </View>

      <TextField
        label="Hours per day"
        value={hoursPerDay}
        onChangeText={onHoursPerDay}
        keyboardType="decimal-pad"
        placeholder={defaults ? `Company default: ${defaults.hoursPerDay}` : 'Company default'}
        helper={
          defaults
            ? `Leave blank to use the company default (${defaults.hoursPerDay} hours).`
            : 'Leave blank to use the company default.'
        }
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
          {describeMask(effectiveDays)} · {scheduledDays} working days this month
        </Text>
      </Card>
    </View>
  );
}
