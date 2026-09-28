import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, ScreenHeader, SelectChips, TextField, type SelectOption } from '@/components/ui';
import { StatutoryBankFields } from '@/components/StatutoryBankFields';
import { useEmployee, useUpdateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { MaritalStatus } from '@/api/types';
import { parseChildren } from '@/lib/employeeFields';
import { palette, font, spacing } from '@/theme';

const MARITAL_OPTIONS: SelectOption<MaritalStatus>[] = [
  { value: 'SINGLE', label: 'Single' },
  { value: 'MARRIED', label: 'Married' },
];

const SPOUSE_OPTIONS: SelectOption<'WORKING' | 'NOT_WORKING'>[] = [
  { value: 'NOT_WORKING', label: 'Not working' },
  { value: 'WORKING', label: 'Working' },
];

/**
 * Employee hub → **Statutory & bank**. Owns the identity numbers (NRIC, EPF,
 * SOCSO, LHDN) and bank payout details, plus the tax profile that drives the
 * PCB estimate on payslips. Saves only those fields.
 */
export default function EmployeeStatutory() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  const update = useUpdateEmployee();

  const [nric, setNric] = useState('');
  const [epfNo, setEpfNo] = useState('');
  const [socsoNo, setSocsoNo] = useState('');
  const [taxNo, setTaxNo] = useState('');
  const [bankName, setBankName] = useState('');
  const [bankAccountNo, setBankAccountNo] = useState('');
  const [maritalStatus, setMaritalStatus] = useState<MaritalStatus>('SINGLE');
  const [spouseWorking, setSpouseWorking] = useState<'WORKING' | 'NOT_WORKING'>('NOT_WORKING');
  const [children, setChildren] = useState('');
  const [seeded, setSeeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (seeded || !detail.data) return;
    const d = detail.data;
    setNric(d.nric ?? '');
    setEpfNo(d.epfNo ?? '');
    setSocsoNo(d.socsoNo ?? '');
    setTaxNo(d.taxNo ?? '');
    setBankName(d.bankName ?? '');
    setBankAccountNo(d.bankAccountNo ?? '');
    setMaritalStatus(d.maritalStatus === 'MARRIED' ? 'MARRIED' : 'SINGLE');
    setSpouseWorking(d.spouseWorking ? 'WORKING' : 'NOT_WORKING');
    setChildren(String(d.numChildren));
    setSeeded(true);
  }, [seeded, detail.data]);

  const onSave = async () => {
    if (update.isPending) return;
    setError(null);
    const numChildren = parseChildren(children);
    if (children.trim() && numChildren === undefined) {
      setError('Enter a valid number of children, or leave it blank.');
      return;
    }
    try {
      await update.mutateAsync({
        id: params.id,
        body: {
          nric: nric.trim(),
          epfNo: epfNo.trim(),
          socsoNo: socsoNo.trim(),
          taxNo: taxNo.trim(),
          bankName: bankName.trim(),
          bankAccountNo: bankAccountNo.trim(),
          maritalStatus,
          spouseWorking: maritalStatus === 'MARRIED' ? spouseWorking === 'WORKING' : undefined,
          numChildren,
        },
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes. Check your connection.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Statutory & bank" subtitle={detail.data?.fullName ?? params.name} />

      <AsyncBoundary loading={detail.isLoading} error={detail.error} onRetry={detail.refetch}>
        <View style={{ gap: 14 }}>
          <StatutoryBankFields
            nric={nric}
            onNric={setNric}
            epfNo={epfNo}
            onEpfNo={setEpfNo}
            socsoNo={socsoNo}
            onSocsoNo={setSocsoNo}
            taxNo={taxNo}
            onTaxNo={setTaxNo}
            bankName={bankName}
            onBankName={setBankName}
            bankAccountNo={bankAccountNo}
            onBankAccountNo={setBankAccountNo}
          />

          <View style={{ gap: 14, marginTop: spacing.xs }}>
            <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Tax profile</Text>
            <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
              Used to estimate monthly income tax (PCB) on payslips.
            </Text>
            <SelectChips label="Marital status" options={MARITAL_OPTIONS} value={maritalStatus} onChange={setMaritalStatus} />
            {maritalStatus === 'MARRIED' && (
              <SelectChips label="Spouse" options={SPOUSE_OPTIONS} value={spouseWorking} onChange={setSpouseWorking} />
            )}
            <TextField
              label="Number of children"
              value={children}
              onChangeText={setChildren}
              keyboardType="number-pad"
              placeholder="e.g. 2"
            />
          </View>

          {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

          <View style={{ marginTop: spacing.sm }}>
            <Button
              label={update.isPending ? 'Saving…' : 'Save statutory & bank'}
              icon="check"
              disabled={update.isPending}
              onPress={onSave}
            />
          </View>
        </View>
      </AsyncBoundary>
    </Screen>
  );
}
