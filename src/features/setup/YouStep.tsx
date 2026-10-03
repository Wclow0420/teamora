import React from 'react';
import { View } from 'react-native';
import { TextField } from '@/components/ui';
import { PASSWORD_HELPER } from '@/lib/password';
import type { AccountErrors, AccountForm } from './types';

type Props = {
  form: AccountForm;
  errors: AccountErrors;
  onChange: (patch: Partial<AccountForm>) => void;
};

/** "First, about you" — the owner's own sign-in details. */
export function YouStep({ form, errors, onChange }: Props) {
  return (
    <View style={{ gap: 16 }}>
      <TextField
        label="Full name"
        icon="user"
        value={form.fullName}
        onChangeText={(fullName) => onChange({ fullName })}
        autoCapitalize="words"
        maxLength={100}
        error={errors.fullName}
      />
      <TextField
        label="Work email"
        icon="mail"
        value={form.email}
        onChangeText={(email) => onChange({ email })}
        keyboardType="email-address"
        autoCapitalize="none"
        maxLength={254}
        error={errors.email}
        helper="You'll sign in with this."
      />
      <TextField
        label="Password"
        icon="lock"
        value={form.password}
        onChangeText={(password) => onChange({ password })}
        secure
        autoCapitalize="none"
        helper={PASSWORD_HELPER}
        error={errors.password}
      />
    </View>
  );
}
