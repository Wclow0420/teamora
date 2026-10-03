import React from 'react';
import { Text, View } from 'react-native';
import { Stepper } from '@/components/ui';
import type { LeaveTypeDef } from '@/api/types';
import { palette, type } from '@/theme';

/** Server limit for a leave type's yearly entitlement. */
export const LEAVE_DAYS_MAX = 366;

type Props = {
  /** Leave types with a yearly entitlement (accrual ≠ NONE), in display order. */
  tracked: LeaveTypeDef[];
  /** Types with no yearly limit (e.g. Unpaid Leave) — mentioned, not edited here. */
  untracked: LeaveTypeDef[];
  /** Days per type id. */
  days: Record<string, number>;
  onChange: (id: string, value: number) => void;
};

/** "Leave your team gets" — one stepper per tracked leave type. */
export function LeaveStep({ tracked, untracked, days, onChange }: Props) {
  return (
    <View style={{ gap: 12 }}>
      {tracked.length === 0 && (
        <Text style={[type.body, { color: palette.soft }]}>
          Your company has no leave types with a yearly entitlement yet. You can add them later in Company settings.
        </Text>
      )}
      {tracked.map((t) => (
        <Stepper
          key={t.id}
          label={t.name}
          caption={`${t.paid ? 'Paid' : 'Unpaid'} · days a year`}
          value={days[t.id] ?? t.defaultEntitlementDays}
          onChange={(v) => onChange(t.id, v)}
          min={0}
          max={LEAVE_DAYS_MAX}
          format={(n) => `${n} ${n === 1 ? 'day' : 'days'}`}
        />
      ))}
      <Text style={[type.meta, { lineHeight: 18, color: palette.faint, marginTop: 4 }]}>
        Pre-filled with common Malaysian defaults. The Employment Act minimum is 8 days annual leave and 14 days
        sick leave a year for staff with under 2 years' service — don't go below that.
        {untracked.length > 0
          ? ` ${untracked.map((t) => t.name).join(', ')} ${untracked.length === 1 ? 'has' : 'have'} no yearly limit.`
          : ''}
      </Text>
    </View>
  );
}
