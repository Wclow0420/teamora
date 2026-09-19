import React, { useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, TextField } from '@/components/ui';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/api/client';
import { palette, font } from '@/theme';

export default function Register() {
  const router = useRouter();
  const { signUp } = useAuth();

  const [companyName, setCompanyName] = useState('');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async () => {
    setError(null);
    if (!companyName.trim() || !fullName.trim() || !email.trim() || !password) {
      setError('Please fill in all fields.');
      return;
    }
    setSubmitting(true);
    try {
      await signUp(companyName.trim(), fullName.trim(), email.trim(), password);
      router.replace('/admin/dashboard');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen paddingX={24}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={[font(800), { fontSize: 20, color: palette.ink, letterSpacing: -0.5 }]}>lumi</Text>
        <Pressable onPress={() => router.back()}>
          <Text style={[font(600), { fontSize: 13, color: palette.faint }]}>Back</Text>
        </Pressable>
      </View>

      <View style={{ marginTop: 28 }}>
        <Text style={[font(800), { fontSize: 28, color: palette.ink, letterSpacing: -0.8 }]}>Create your company</Text>
        <Text style={[font(500), { fontSize: 13.5, lineHeight: 20, color: palette.soft, marginTop: 8 }]}>
          You'll be set up as the owner.
        </Text>
      </View>

      <View style={{ gap: 14, marginTop: 24 }}>
        <TextField label="Company name" value={companyName} onChangeText={setCompanyName} autoCapitalize="words" />
        <TextField label="Your name" value={fullName} onChangeText={setFullName} autoCapitalize="words" />
        <TextField
          label="Work email"
          icon="mail"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <TextField label="Password" icon="lock" value={password} onChangeText={setPassword} secure />

        {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

        <Button
          label={submitting ? 'Creating…' : 'Create company'}
          onPress={onSubmit}
          disabled={submitting}
          style={{ marginTop: 4 }}
        />
      </View>
    </Screen>
  );
}
