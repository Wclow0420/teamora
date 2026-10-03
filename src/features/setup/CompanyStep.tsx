import React from 'react';
import { Text, View } from 'react-native';
import { TextField } from '@/components/ui';
import { palette, type } from '@/theme';
import type { CompanyErrors, CompanyForm } from './types';

type Props = {
  form: CompanyForm;
  errors: CompanyErrors;
  onChange: (patch: Partial<CompanyForm>) => void;
  /** The account already exists — the name is fixed here (edit it in Company settings). */
  locked: boolean;
  onOpenTerms: () => void;
  onOpenPrivacy: () => void;
};

/** "Your company" — the name staff will see, and the optional SSM number. */
export function CompanyStep({ form, errors, onChange, locked, onOpenTerms, onOpenPrivacy }: Props) {
  return (
    <View style={{ gap: 16 }}>
      {locked ? (
        <View>
          <Text style={[type.label, { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>Company name</Text>
          <Text style={[type.h3, { color: palette.ink }]}>{form.companyName.trim()}</Text>
        </View>
      ) : (
        <TextField
          label="Company name"
          icon="building"
          value={form.companyName}
          onChangeText={(companyName) => onChange({ companyName })}
          autoCapitalize="words"
          maxLength={150}
          error={errors.companyName}
          helper="As your team knows it — it appears on payslips."
        />
      )}
      <TextField
        label="SSM registration no. (optional)"
        value={form.registrationNo}
        onChangeText={(registrationNo) => onChange({ registrationNo })}
        autoCapitalize="characters"
        maxLength={64}
        error={errors.registrationNo}
        helper="You can add this later in Company settings."
      />
      {!locked && (
        <Text style={[type.meta, { lineHeight: 18, color: palette.faint, marginTop: 4 }]}>
          By creating a company you agree to Teamora's{' '}
          <Text accessibilityRole="link" onPress={onOpenTerms} style={[type.label, { fontSize: 12, color: palette.soft, textDecorationLine: 'underline' }]}>
            Terms
          </Text>
          {' & '}
          <Text accessibilityRole="link" onPress={onOpenPrivacy} style={[type.label, { fontSize: 12, color: palette.soft, textDecorationLine: 'underline' }]}>
            Privacy
          </Text>
          . You'll be set up as the owner.
        </Text>
      )}
    </View>
  );
}
