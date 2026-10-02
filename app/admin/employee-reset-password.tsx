import React, { useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Card, Icon, ScreenHeader, TextField } from '@/components/ui';
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from '@/components/account/ChangePasswordScreen';
import { useResetEmployeePassword } from '@/api/queries';
import { ApiError } from '@/api/client';
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
    if (password.length < MIN_PASSWORD_LENGTH) {
      setFieldError(`Use at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
      setFieldError(`Keep it to ${MAX_PASSWORD_LENGTH} characters or fewer.`);
      return;
    }
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
          placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
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
