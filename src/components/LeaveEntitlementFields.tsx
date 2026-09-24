import React, { useState } from 'react';
import { View, Text, type TextStyle } from 'react-native';
import { Button, Card, IconTile, TextField } from '@/components/ui';
import { useAdminLeaveBalances, useOverrideLeaveEntitlement } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import { ApiError } from '@/api/client';
import type { LeaveBalance } from '@/api/types';
import { formatDecimal } from '@/lib/numbers';
import { palette, font, radius } from '@/theme';

/** Entitlement days are fractional — keep the digits aligned. */
const NUM: TextStyle = { fontVariant: ['tabular-nums'] };

/** A number the API may not have sent yet (older backend) → 0. */
function num(value: number | undefined): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

/** Parse a days input → a non-negative number, or undefined if blank/invalid. */
function parseEntitledDays(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

/** "3 used · 2 carried over" — only the parts that say something. */
function contextLine(b: LeaveBalance): string {
  const parts = [`${formatDecimal(num(b.used))} used`];
  const carried = num(b.carriedForward);
  if (carried > 0) parts.push(`${formatDecimal(carried)} carried over`);
  return parts.join(' · ');
}

type Props = {
  /** The employee being edited. */
  employeeId: string;
};

/**
 * "Leave entitlement" section for the edit-employee screen: one editable
 * full-year entitlement per leave type for the current leave year, saved
 * through the admin override endpoint.
 *
 * Deliberately self-contained — it saves on its own button, separate from the
 * employee's main "Save changes" — and renders nothing when the balances can't
 * be loaded, so an older backend or a permission error never breaks the form.
 */
export function LeaveEntitlementFields({ employeeId }: Props) {
  const balances = useAdminLeaveBalances(employeeId);
  const override = useOverrideLeaveEntitlement();

  /** Only user-typed values live here; everything else reads from the server. */
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const rows = balances.data ?? [];
  // Nothing to edit (still loading, failed, or no leave types) → show nothing.
  if (rows.length === 0) return null;

  const valueFor = (b: LeaveBalance): string => edits[b.leaveTypeId] ?? formatDecimal(num(b.entitled));
  const dirtyRows = rows.filter((b) => {
    const typed = edits[b.leaveTypeId];
    return typed !== undefined && typed.trim() !== formatDecimal(num(b.entitled));
  });

  const leaveYear = rows.find((b) => Number.isInteger(b.leaveYear))?.leaveYear;

  const onSave = async () => {
    setError(null);
    setSaved(false);
    const payloads: { leaveTypeId: string; entitled: number; leaveYear?: number }[] = [];
    for (const b of dirtyRows) {
      const entitled = parseEntitledDays(edits[b.leaveTypeId] ?? '');
      if (entitled === undefined) {
        setError(`Enter a valid number of days for ${b.name}.`);
        return;
      }
      payloads.push({
        leaveTypeId: b.leaveTypeId,
        entitled,
        leaveYear: Number.isInteger(b.leaveYear) ? b.leaveYear : undefined,
      });
    }
    try {
      for (const body of payloads) {
        await override.mutateAsync({ employeeId, ...body });
      }
      setEdits({});
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save entitlement. Check your connection.');
    }
  };

  return (
    <View style={{ gap: 12, marginTop: 4 }}>
      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Leave entitlement</Text>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
        Full-year entitlement{leaveYear ? ` for the ${leaveYear} leave year` : ''}. Accrual and carried-over days
        are applied on top of this.
      </Text>

      <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
        {rows.map((b, i) => {
          const accent = accentFromKey(b.colorKey);
          return (
            <View
              key={b.leaveTypeId}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 12,
                paddingVertical: 12,
                paddingHorizontal: 14,
                borderTopWidth: i ? 1 : 0,
                borderTopColor: palette.line,
              }}
            >
              <IconTile icon="sun" color={accent.color} background={accent.bg} size={34} iconSize={17} cornerRadius={11} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={[font(700), { fontSize: 13, color: palette.ink }]} numberOfLines={1}>
                  {b.name}
                </Text>
                <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 4 }, NUM]} numberOfLines={1}>
                  {contextLine(b)}
                </Text>
              </View>
              <View style={{ width: 84 }}>
                <TextField
                  value={valueFor(b)}
                  onChangeText={(t) => setEdits((prev) => ({ ...prev, [b.leaveTypeId]: t }))}
                  keyboardType="decimal-pad"
                  placeholder="0"
                />
              </View>
            </View>
          );
        })}
      </Card>

      {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}
      {saved && !error && <Text style={[font(600), { fontSize: 12.5, color: palette.sage }]}>Entitlement saved.</Text>}

      <Button
        label={override.isPending ? 'Saving…' : 'Save entitlement'}
        variant="light"
        disabled={override.isPending || dirtyRows.length === 0}
        onPress={onSave}
      />
    </View>
  );
}
