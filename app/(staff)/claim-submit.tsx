import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, DateField, toISODate, type SelectOption } from '@/components/ui';
import { useSubmitClaim } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette } from '@/theme';

type Category = 'TRAVEL' | 'PETROL' | 'MEAL' | 'MEDICAL' | 'OTHER';

const CATEGORIES: SelectOption<Category>[] = [
  { value: 'TRAVEL', label: 'Travel' },
  { value: 'PETROL', label: 'Petrol' },
  { value: 'MEAL', label: 'Meal' },
  { value: 'MEDICAL', label: 'Medical' },
  { value: 'OTHER', label: 'Other' },
];

export default function ClaimSubmit() {
  const router = useRouter();
  const submit = useSubmitClaim();

  const today = new Date();
  const [category, setCategory] = useState<Category>('TRAVEL');
  const [title, setTitle] = useState('');
  const [amountText, setAmountText] = useState('');
  const [date, setDate] = useState<Date>(today);

  const [titleError, setTitleError] = useState<string | null>(null);
  const [amountError, setAmountError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    setTitleError(null);
    setAmountError(null);

    const trimmedTitle = title.trim();
    const amount = parseFloat(amountText);
    let valid = true;

    if (!trimmedTitle) {
      setTitleError('Please enter a title.');
      valid = false;
    }
    if (!(amount > 0)) {
      setAmountError('Enter an amount greater than 0.');
      valid = false;
    }
    if (!valid) return;

    try {
      await submit.mutateAsync({
        category,
        title: trimmedTitle,
        amount,
        claimDate: toISODate(date),
      });
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="New claim" />

      <View style={{ gap: 14, marginTop: 8 }}>
        <SelectChips label="Category" options={CATEGORIES} value={category} onChange={setCategory} />
        <TextField label="Title" placeholder="e.g. Grab to client" value={title} onChangeText={setTitle} error={titleError} />
        <TextField
          label="Amount (RM)"
          value={amountText}
          onChangeText={setAmountText}
          keyboardType="decimal-pad"
          error={amountError}
        />
        <DateField label="Date" value={date} onChange={setDate} />

        {error && <Text style={{ fontSize: 13, color: palette.danger }}>{error}</Text>}

        <Button
          label={submit.isPending ? 'Submitting…' : 'Submit claim'}
          onPress={onSubmit}
          disabled={submit.isPending}
          style={{ marginTop: 4 }}
        />
      </View>
    </Screen>
  );
}
