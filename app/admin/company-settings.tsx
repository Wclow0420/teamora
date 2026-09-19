import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useCompany, useUpdateCompany } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { CompanyResponse } from '@/api/types';
import { palette } from '@/theme';

export default function CompanySettings() {
  const q = useCompany();

  return (
    <Screen>
      <ScreenHeader back title="Company settings" />
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && <CompanyForm company={q.data} />}
      </AsyncBoundary>
    </Screen>
  );
}

function CompanyForm({ company }: { company: CompanyResponse }) {
  const router = useRouter();
  const update = useUpdateCompany();

  const [name, setName] = useState(company.name ?? '');
  const [registrationNo, setRegistrationNo] = useState(company.registrationNo ?? '');
  const [epfNo, setEpfNo] = useState(company.epfNo ?? '');
  const [socsoNo, setSocsoNo] = useState(company.socsoNo ?? '');
  const [email, setEmail] = useState(company.email ?? '');
  const [phone, setPhone] = useState(company.phone ?? '');
  const [address, setAddress] = useState(company.address ?? '');
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    try {
      await update.mutateAsync({ name, registrationNo, epfNo, socsoNo, email, phone, address });
      router.back();
    } catch (e) {
      if (e instanceof ApiError) setError(e.message);
      else setError('Something went wrong. Please try again.');
    }
  };

  return (
    <View style={{ gap: 14, marginTop: 8 }}>
      <TextField label="Company name" value={name} onChangeText={setName} />
      <TextField label="Registration no. (SSM)" value={registrationNo} onChangeText={setRegistrationNo} />
      <TextField label="Employer EPF no." value={epfNo} onChangeText={setEpfNo} />
      <TextField label="Employer SOCSO no." value={socsoNo} onChangeText={setSocsoNo} />
      <TextField
        label="Email"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <TextField label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
      <TextField label="Address" value={address} onChangeText={setAddress} multiline />

      {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

      <Button label={update.isPending ? 'Saving…' : 'Save changes'} onPress={onSubmit} disabled={update.isPending} />
    </View>
  );
}
