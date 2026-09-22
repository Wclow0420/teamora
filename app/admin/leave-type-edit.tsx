import React, { useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, type SelectOption } from '@/components/ui';
import { useCreateLeaveType, useUpdateLeaveType } from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import { ApiError } from '@/api/client';
import type { LeaveAccrual } from '@/api/types';
import { palette, font, radius } from '@/theme';

const COLOR_KEYS = ['coral', 'amber', 'sage', 'violet'] as const;

const PAID_OPTIONS: SelectOption<'PAID' | 'UNPAID'>[] = [
  { value: 'PAID', label: 'Paid' },
  { value: 'UNPAID', label: 'Unpaid (deducts salary)' },
];

const ACCRUAL_OPTIONS: SelectOption<LeaveAccrual>[] = [
  { value: 'FIXED_ANNUAL', label: 'Fixed annual' },
  { value: 'MONTHLY_ACCRUAL', label: 'Monthly accrual' },
  { value: 'NONE', label: 'None' },
];

const ACTIVE_OPTIONS: SelectOption<'ACTIVE' | 'INACTIVE'>[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Hidden' },
];

/** Derive a stable code from a name: uppercase, non-alphanumerics → underscore. */
function codeFromName(name: string): string {
  return name.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 24);
}

function parseDays(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return 0;
  const n = Number(trimmed);
  return Number.isInteger(n) && n >= 0 ? n : undefined;
}

export default function LeaveTypeEdit() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    id?: string;
    name?: string;
    code?: string;
    paid?: string;
    defaultEntitlementDays?: string;
    accrual?: string;
    colorKey?: string;
    active?: string;
  }>();
  const isEdit = !!params.id;

  const create = useCreateLeaveType();
  const update = useUpdateLeaveType();

  const [name, setName] = useState(params.name ?? '');
  const [code, setCode] = useState(params.code ?? '');
  const [paid, setPaid] = useState<'PAID' | 'UNPAID'>(params.paid === 'false' ? 'UNPAID' : 'PAID');
  const [days, setDays] = useState(params.defaultEntitlementDays ?? '');
  const [accrual, setAccrual] = useState<LeaveAccrual>(
    params.accrual === 'MONTHLY_ACCRUAL' || params.accrual === 'NONE' ? params.accrual : 'FIXED_ANNUAL',
  );
  const [colorKey, setColorKey] = useState<string>(params.colorKey ?? 'coral');
  const [active, setActive] = useState<'ACTIVE' | 'INACTIVE'>(params.active === 'false' ? 'INACTIVE' : 'ACTIVE');
  const [error, setError] = useState<string | null>(null);

  const busy = create.isPending || update.isPending;

  const onSubmit = async () => {
    setError(null);
    if (!name.trim()) {
      setError('Give the leave type a name.');
      return;
    }
    const entitlement = parseDays(days);
    if (entitlement === undefined) {
      setError('Enter a valid number of entitlement days.');
      return;
    }
    try {
      if (isEdit && params.id) {
        await update.mutateAsync({
          id: params.id,
          body: {
            name: name.trim(),
            paid: paid === 'PAID',
            defaultEntitlementDays: entitlement,
            accrual,
            colorKey,
            active: active === 'ACTIVE',
          },
        });
      } else {
        const finalCode = (code.trim() || codeFromName(name)).toUpperCase();
        if (!finalCode) {
          setError('Enter a code (e.g. ANNUAL).');
          return;
        }
        await create.mutateAsync({
          name: name.trim(),
          code: finalCode,
          paid: paid === 'PAID',
          defaultEntitlementDays: entitlement,
          accrual,
          colorKey,
        });
      }
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title={isEdit ? 'Edit leave type' : 'New leave type'} />

      <View style={{ gap: 14, marginTop: 8 }}>
        <TextField label="Name" value={name} onChangeText={setName} placeholder="e.g. Annual Leave" />
        {isEdit ? (
          <View>
            <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>Code</Text>
            <Text style={[font(600), { fontSize: 13, color: palette.faint }]}>{params.code}</Text>
          </View>
        ) : (
          <TextField
            label="Code"
            value={code}
            onChangeText={(t) => setCode(t.toUpperCase())}
            autoCapitalize="characters"
            placeholder={name ? codeFromName(name) : 'e.g. ANNUAL'}
          />
        )}
        <SelectChips label="Payment" options={PAID_OPTIONS} value={paid} onChange={setPaid} />
        {paid === 'UNPAID' && (
          <Text style={[font(500), { fontSize: 11.5, color: palette.soft, marginTop: -8, lineHeight: 16 }]}>
            Days taken under this type are deducted from salary at the employee's derived daily rate.
          </Text>
        )}
        <TextField
          label="Default entitlement (days / year)"
          value={days}
          onChangeText={setDays}
          keyboardType="number-pad"
          placeholder="e.g. 16"
        />
        <SelectChips label="Accrual" options={ACCRUAL_OPTIONS} value={accrual} onChange={setAccrual} />

        {/* colour picker */}
        <View>
          <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 8 }]}>Colour</Text>
          <View style={{ flexDirection: 'row', gap: 12 }}>
            {COLOR_KEYS.map((key) => {
              const accent = accentFromKey(key);
              const on = key === colorKey;
              return (
                <Pressable
                  key={key}
                  onPress={() => setColorKey(key)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: on }}
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: radius.md,
                    backgroundColor: accent.bg,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderWidth: on ? 2 : 1,
                    borderColor: on ? accent.color : palette.line,
                  }}
                >
                  <View style={{ width: 16, height: 16, borderRadius: 8, backgroundColor: accent.color }} />
                </Pressable>
              );
            })}
          </View>
        </View>

        {isEdit && <SelectChips label="Visibility" options={ACTIVE_OPTIONS} value={active} onChange={setActive} />}

        {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

        <Button
          label={busy ? 'Saving…' : isEdit ? 'Save changes' : 'Create leave type'}
          onPress={onSubmit}
          disabled={busy}
          style={{ marginTop: 4 }}
        />
      </View>
    </Screen>
  );
}
