import React, { useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Card, Icon, ScreenHeader, TextField } from '@/components/ui';
import { useResetEmployeePassword } from '@/api/queries';
import { ApiError } from '@/api/client';
import { PASSWORD_HELPER, passwordError } from '@/lib/password';
import { palette, font, radius, spacing, tint } from '@/theme';

/**
 * Employee hub → Employment → **Reset password**. An OWNER/HR_ADMIN sets a
 * temporary password for a team member who's locked out (there's no email
 * reset). The field is shown in plain text on purpose — the admin has to read
 * it out / pass it on.
 */
export default function EmployeeResetPassword() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const reset = useResetEmployeePassword();
  const name = params.name ?? 'this employee';

  const [password, setPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    if (reset.isPending) return;
    setError(null);
    setFieldError(null);
    const rule = passwordError(password);
    if (rule) {
      setFieldError(rule);
      return;
    }
    // Replacing someone's password signs them out everywhere — ask first.
    Alert.alert(
      `Reset ${name}'s password?`,
      'Their current password stops working and they will be signed out of their devices.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Reset password', style: 'destructive', onPress: () => void doReset() },
      ],
    );
  };

  const doReset = async () => {
    try {
      await reset.mutateAsync({ id: params.id, newPassword: password });
      Alert.alert('Password reset', `Share the temporary password with ${name} so they can sign in.`);
      if (router.canGoBack()) router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reset the password. Check your connection and try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Reset password" subtitle={params.name} />

      <View style={{ gap: 14, marginTop: spacing.sm }}>
        <TextField
          label="Temporary password"
          icon="lock"
          value={password}
          onChangeText={setPassword}
          autoCapitalize="none"
          helper={PASSWORD_HELPER}
          error={fieldError}
        />

        <Card
          padding={spacing.md}
          elevated={false}
          style={{ flexDirection: 'row', gap: spacing.md, borderRadius: radius.lg, backgroundColor: tint.amber, borderColor: tint.amber }}
        >
          <Icon name="shield" size={18} color={palette.amber} />
          <Text style={[font(500), { flex: 1, fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>
            Share this password with {name} privately — it replaces their current one straight away. Once they're
            signed in, they can set their own under Profile → Change password.
          </Text>
        </Card>

        {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

        <Button
          label={reset.isPending ? 'Resetting…' : 'Reset password'}
          icon="lock"
          disabled={reset.isPending}
          onPress={onSubmit}
        />
      </View>
    </Screen>
  );
}
