import React, { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Icon, ScreenHeader, TextField } from '@/components/ui';
import { useChangePassword } from '@/api/queries';
import { ApiError } from '@/api/client';
import { palette, font, spacing } from '@/theme';

/** Server rule (mirrored client-side so the user hears about it before a round-trip). */
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 72;

/**
 * Change-my-password screen, shared by the staff and admin stacks (each has a
 * thin `change-password` route that renders this, so "back" returns to the
 * Profile tab it was opened from).
 */
export function ChangePasswordScreen() {
  const router = useRouter();
  const change = useChangePassword();

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false);

  const [currentError, setCurrentError] = useState<string | null>(null);
  const [nextError, setNextError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    if (change.isPending) return;
    setError(null);
    setCurrentError(null);
    setNextError(null);
    setConfirmError(null);

    let valid = true;
    if (!current) {
      setCurrentError('Enter your current password.');
      valid = false;
    }
    if (next.length < MIN_PASSWORD_LENGTH) {
      setNextError(`Use at least ${MIN_PASSWORD_LENGTH} characters.`);
      valid = false;
    } else if (next.length > MAX_PASSWORD_LENGTH) {
      setNextError(`Keep it to ${MAX_PASSWORD_LENGTH} characters or fewer.`);
      valid = false;
    } else if (next === current) {
      setNextError('Choose a password different from your current one.');
      valid = false;
    }
    if (confirm !== next) {
      setConfirmError("This doesn't match your new password.");
      valid = false;
    }
    if (!valid) return;

    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next });
      Alert.alert('Password changed', "Use your new password the next time you sign in. You've been signed out on your other devices.");
      if (router.canGoBack()) router.back();
    } catch (e) {
      if (e instanceof ApiError) {
        // "Current password is incorrect" belongs under that field; anything
        // else (e.g. a server-side rule on the new password) goes below the form.
        if (/incorrect/i.test(e.message)) setCurrentError(e.message);
        else setError(e.message);
      } else {
        setError('Could not change your password. Check your connection and try again.');
      }
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Change password" />

      <View style={{ gap: 14, marginTop: spacing.sm }}>
        <TextField
          label="Current password"
          icon="lock"
          value={current}
          onChangeText={setCurrent}
          secure={!show}
          autoCapitalize="none"
          error={currentError}
        />
        <TextField
          label="New password"
          icon="lock"
          value={next}
          onChangeText={setNext}
          secure={!show}
          autoCapitalize="none"
          placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
          error={nextError}
        />
        <TextField
          label="Confirm new password"
          icon="lock"
          value={confirm}
          onChangeText={setConfirm}
          secure={!show}
          autoCapitalize="none"
          error={confirmError}
        />

        <Pressable
          onPress={() => setShow((s) => !s)}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: show }}
          hitSlop={8}
          style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, alignSelf: 'flex-start' }}
        >
          <View
            style={{
              width: 20,
              height: 20,
              borderRadius: 6,
              borderWidth: 1.5,
              borderColor: show ? palette.coral : palette.inactive,
              backgroundColor: show ? palette.coral : palette.surface,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {show && <Icon name="check" size={13} color={palette.white} stroke={2.6} />}
          </View>
          <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>Show passwords</Text>
        </Pressable>

        {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

        <Button
          label={change.isPending ? 'Saving…' : 'Change password'}
          icon="check"
          disabled={change.isPending}
          onPress={onSubmit}
          style={{ marginTop: spacing.xs }}
        />

        <Text style={[font(500), { fontSize: 11.5, lineHeight: 17, color: palette.faint }]}>
          Forgotten your current password? Ask your HR admin to reset it for you.
        </Text>
      </View>
    </Screen>
  );
}
