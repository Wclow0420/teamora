import React, { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Card, TextField } from '@/components/ui';
import { useForgotPassword, useResetPasswordWithCode } from '@/api/queries';
import { showInternalHints } from '@/lib/env';
import { errorMessage } from '@/lib/errors';
import { PASSWORD_HELPER, passwordError } from '@/lib/password';
import { palette, font, radius, gradients, spacing, tint } from '@/theme';

const CODE_LENGTH = 6;
const EMAIL_SHAPE = /^\S+@\S+\.\S+$/;

/**
 * Signed-out password recovery by one-time code.
 *   step 1 — enter the work email → the server issues a 6-digit code
 *   step 2 — enter the code + a new password → back to sign in
 *
 * The server answers step 1 identically whether or not the email has an
 * account, so the copy never confirms that one exists.
 */
export default function ForgotPassword() {
  const router = useRouter();
  const request = useForgotPassword();
  const reset = useResetPasswordWithCode();

  const [step, setStep] = useState<'email' | 'code'>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  /** Only ever set by a local/dev backend; shown only when internal hints are on. */
  const [devCode, setDevCode] = useState<string | null>(null);
  const [resent, setResent] = useState(false);

  const [emailError, setEmailError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [passwordFieldError, setPasswordFieldError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cleanEmail = email.trim().toLowerCase();

  const backToLogin = () => (router.canGoBack() ? router.back() : router.replace('/login'));

  /** Ask the server for a code. Returns whether the request went through. */
  const requestCode = async (): Promise<boolean> => {
    setError(null);
    try {
      const res = await request.mutateAsync(cleanEmail);
      setDevCode(showInternalHints() && res?.devCode ? res.devCode : null);
      return true;
    } catch (e) {
      setError(errorMessage(e));
      return false;
    }
  };

  const onSendCode = async () => {
    if (request.isPending) return;
    setEmailError(null);
    if (!EMAIL_SHAPE.test(cleanEmail)) {
      setEmailError('Enter your work email address.');
      return;
    }
    if (await requestCode()) {
      setResent(false);
      setStep('code');
    }
  };

  const onResend = async () => {
    if (request.isPending || reset.isPending) return;
    setResent(false);
    setCodeError(null);
    if (await requestCode()) {
      setCode('');
      setResent(true);
    }
  };

  const onReset = async () => {
    if (reset.isPending) return;
    setError(null);
    setCodeError(null);
    setPasswordFieldError(null);
    setConfirmError(null);

    let valid = true;
    if (code.length !== CODE_LENGTH) {
      setCodeError(`Enter the ${CODE_LENGTH}-digit code.`);
      valid = false;
    }
    const rule = passwordError(password);
    if (rule) {
      setPasswordFieldError(rule);
      valid = false;
    }
    if (confirm !== password) {
      setConfirmError("This doesn't match your new password.");
      valid = false;
    }
    if (!valid) return;

    try {
      await reset.mutateAsync({ email: cleanEmail, code, newPassword: password });
      Alert.alert('Password changed', 'Sign in with your new password. Your other devices have been signed out.');
      backToLogin();
    } catch (e) {
      const message = errorMessage(e);
      // The server's "code is incorrect or has expired" belongs under the code field.
      if (/code/i.test(message)) setCodeError(message);
      else setError(message);
    }
  };

  return (
    <Screen paddingX={24}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 9 }}>
          <LinearGradient colors={gradients.avatar} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ width: 34, height: 34, borderRadius: 10 }} />
          <Text style={[font(800), { fontSize: 20, color: palette.ink, letterSpacing: -0.5 }]}>Teamora</Text>
        </View>
        <Pressable onPress={backToLogin} accessibilityRole="button" accessibilityLabel="Back to sign in" hitSlop={8}>
          <Text style={[font(600), { fontSize: 13, color: palette.faint }]}>Back</Text>
        </Pressable>
      </View>

      <View style={{ marginTop: 36 }}>
        <Text style={[font(800), { fontSize: 28, lineHeight: 35, color: palette.ink, letterSpacing: -0.8 }]}>
          {step === 'email' ? 'Forgot your password?' : 'Enter your code'}
        </Text>
        <Text style={[font(500), { fontSize: 13.5, lineHeight: 20, color: palette.soft, marginTop: 8 }]}>
          {step === 'email'
            ? "Enter your work email and we'll send you a 6-digit code to set a new password."
            : "If that email has a Teamora account, we've sent it a 6-digit code."}
        </Text>
        {step === 'code' && (
          <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.faint, marginTop: 6 }]}>
            {cleanEmail} · the code lasts 10 minutes
          </Text>
        )}
      </View>

      {step === 'email' ? (
        <View style={{ marginTop: 26, gap: 14 }}>
          <TextField
            label="Work email"
            icon="mail"
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            placeholder="you@company.com"
            error={emailError}
          />
          {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}
          <Button
            label={request.isPending ? 'Sending…' : 'Send code'}
            icon="arrowR"
            iconTrailing
            height={54}
            disabled={request.isPending}
            onPress={onSendCode}
            style={{ marginTop: spacing.xs }}
          />
          <Text style={[font(500), { fontSize: 11.5, lineHeight: 17, color: palette.faint }]}>
            No code? Your HR admin can also reset your password for you.
          </Text>
        </View>
      ) : (
        <View style={{ marginTop: 26, gap: 14 }}>
          {devCode && (
            <Card padding={0} elevated={false} style={{ borderRadius: radius.lg, padding: 14, backgroundColor: tint.amber }}>
              <Text style={[font(700), { fontSize: 11, letterSpacing: 0.8, textTransform: 'uppercase', color: palette.amber }]}>
                Dev only
              </Text>
              <Text style={[font(500), { fontSize: 12.5, lineHeight: 18, color: palette.soft, marginTop: 6 }]}>
                No email is sent from this server. Your code is{' '}
                <Text style={[font(800), { color: palette.ink, fontVariant: ['tabular-nums'] }]}>{devCode}</Text>
              </Text>
            </Card>
          )}

          <TextField
            label="6-digit code"
            value={code}
            onChangeText={(t) => setCode(t.replace(/\D/g, '').slice(0, CODE_LENGTH))}
            keyboardType="number-pad"
            autoCapitalize="none"
            placeholder="123456"
            error={codeError}
          />
          <TextField
            label="New password"
            icon="lock"
            value={password}
            onChangeText={setPassword}
            secure
            autoCapitalize="none"
            helper={PASSWORD_HELPER}
            error={passwordFieldError}
          />
          <TextField
            label="Confirm new password"
            icon="lock"
            value={confirm}
            onChangeText={setConfirm}
            secure
            autoCapitalize="none"
            error={confirmError}
          />

          {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

          <Button
            label={reset.isPending ? 'Saving…' : 'Set new password'}
            icon="check"
            height={54}
            disabled={reset.isPending}
            onPress={onReset}
            style={{ marginTop: spacing.xs }}
          />

          <View style={{ alignItems: 'center', gap: 6 }}>
            <Pressable
              onPress={onResend}
              disabled={request.isPending}
              accessibilityRole="button"
              accessibilityLabel="Resend code"
              hitSlop={10}
            >
              <Text style={[font(600), { fontSize: 13, lineHeight: 19, color: palette.soft }]}>
                Didn't get it?{' '}
                <Text style={[font(700), { color: palette.coral }]}>{request.isPending ? 'Sending…' : 'Resend code'}</Text>
              </Text>
            </Pressable>
            {resent && (
              <Text style={[font(500), { fontSize: 11.5, lineHeight: 17, color: palette.faint, textAlign: 'center' }]}>
                If that email has a Teamora account, a new code is on its way. Older codes no longer work.
              </Text>
            )}
            <Pressable
              onPress={() => {
                setStep('email');
                setError(null);
                setResent(false);
              }}
              accessibilityRole="button"
              hitSlop={10}
            >
              <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.faint }]}>Use a different email</Text>
            </Pressable>
            <Text style={[font(500), { fontSize: 11.5, lineHeight: 17, color: palette.faint, textAlign: 'center' }]}>
              Still no code? Your HR admin can reset your password for you.
            </Text>
          </View>
        </View>
      )}
    </Screen>
  );
}
