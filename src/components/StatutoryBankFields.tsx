import React from 'react';
import { View, Text } from 'react-native';
import { TextField } from '@/components/ui';
import { palette, font } from '@/theme';

type Props = {
  nric: string;
  onNric: (v: string) => void;
  epfNo: string;
  onEpfNo: (v: string) => void;
  socsoNo: string;
  onSocsoNo: (v: string) => void;
  taxNo: string;
  onTaxNo: (v: string) => void;
  bankName: string;
  onBankName: (v: string) => void;
  bankAccountNo: string;
  onBankAccountNo: (v: string) => void;
};

/**
 * "Statutory & bank" form section: the employee identity numbers (NRIC, EPF,
 * SOCSO, LHDN tax) and bank details used by the payroll/statutory exports and
 * bulk salary payout. All optional. Shared by the add- and edit-employee screens.
 */
export function StatutoryBankFields({
  nric,
  onNric,
  epfNo,
  onEpfNo,
  socsoNo,
  onSocsoNo,
  taxNo,
  onTaxNo,
  bankName,
  onBankName,
  bankAccountNo,
  onBankAccountNo,
}: Props) {
  return (
    <View style={{ gap: 14, marginTop: 4 }}>
      <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Statutory &amp; bank</Text>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
        Used for statutory (EPF/SOCSO/EIS/PCB) reporting and bank salary payout. Optional.
      </Text>

      <TextField
        label="NRIC"
        value={nric}
        onChangeText={onNric}
        autoCapitalize="none"
        placeholder="e.g. 900101-14-5678"
      />
      <TextField
        label="EPF no."
        value={epfNo}
        onChangeText={onEpfNo}
        autoCapitalize="characters"
        placeholder="EPF membership no."
      />
      <TextField
        label="SOCSO no."
        value={socsoNo}
        onChangeText={onSocsoNo}
        autoCapitalize="characters"
        placeholder="SOCSO / PERKESO no."
      />
      <TextField
        label="Tax no. (LHDN)"
        value={taxNo}
        onChangeText={onTaxNo}
        autoCapitalize="characters"
        placeholder="e.g. SG 12345678900"
      />
      <TextField
        label="Bank name"
        value={bankName}
        onChangeText={onBankName}
        autoCapitalize="words"
        placeholder="e.g. Maybank"
      />
      <TextField
        label="Bank account no."
        value={bankAccountNo}
        onChangeText={onBankAccountNo}
        keyboardType="number-pad"
        placeholder="Account number for salary payout"
      />
    </View>
  );
}
