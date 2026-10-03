import React from 'react';
import { Text, View } from 'react-native';
import { Stepper, TimeField, WeekdayToggles } from '@/components/ui';
import { NO_WORKING_DAYS_ERROR, describeMask, isEmptyMask } from '@/lib/workweek';
import { palette, type } from '@/theme';
import type { WorkWeekForm } from './types';

export const HOURS_MIN = 1;
export const HOURS_MAX = 12;
/** Server limit for the late-arrival grace period (minutes). */
export const GRACE_MAX = 120;

type Props = {
  form: WorkWeekForm;
  onChange: (patch: Partial<WorkWeekForm>) => void;
};

/** "Your work week" — the company defaults new staff inherit. */
export function WorkWeekStep({ form, onChange }: Props) {
  const empty = isEmptyMask(form.days);
  return (
    <View style={{ gap: 16 }}>
      <View>
        <WeekdayToggles
          label="Working days"
          value={form.days}
          onChange={(days) => onChange({ days })}
          error={empty ? NO_WORKING_DAYS_ERROR : null}
        />
        {!empty && <Text style={[type.meta, { color: palette.faint, marginTop: 7 }]}>{describeMask(form.days)}</Text>}
      </View>
      <Stepper
        label="Hours per day"
        value={form.hours}
        onChange={(hours) => onChange({ hours })}
        min={HOURS_MIN}
        max={HOURS_MAX}
        step={0.5}
        format={(n) => `${n} h`}
      />
      <TimeField label="Work starts at" value={form.start} onChange={(d) => d && onChange({ start: d })} clearable={false} />
      <Stepper
        label="Late after"
        caption="Minutes past the start time before a clock-in is marked Late."
        value={form.grace}
        onChange={(grace) => onChange({ grace })}
        min={0}
        max={GRACE_MAX}
        step={5}
        format={(n) => `${n} min`}
      />
      <Text style={[type.meta, { lineHeight: 18, color: palette.faint }]}>
        These are your company defaults. Anyone with a different schedule can be set individually later.
      </Text>
    </View>
  );
}
