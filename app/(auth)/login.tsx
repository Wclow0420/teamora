import React, { useState } from 'react';
import { Alert, View, Text, TextInput, Pressable } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Icon } from '@/components/ui';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/api/client';
import { isAdminRole } from '@/api/types';
import { showInternalHints } from '@/lib/env';
import { palette, font, radius, gradients } from '@/theme';

function Field({
  icon,
  placeholder,
  secure,
  value,
  onChangeText,
  keyboardType,
}: {
  icon: 'mail' | 'lock';
  placeholder: string;
  secure?: boolean;
  value: string;
  onChangeText: (t: string) => void;
  keyboardType?: 'email-address' | 'default';
}) {
  return (
    <View
      style={{
        height: 54,
        borderRadius: radius.lg,
        backgroundColor: palette.surface,
        borderWidth: 1,
        borderColor: palette.line,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 10,
        paddingHorizontal: 16,
      }}
    >
      <Icon name={icon} size={19} color={palette.faint} />
      <TextInput
        value={value}
        onChangeText={onChangeText}
        accessibilityLabel={placeholder}
        placeholder={placeholder}
        placeholderTextColor={palette.faint}
        secureTextEntry={secure}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType={keyboardType}
        style={[font(500), { flex: 1, fontSize: 14, color: palette.ink }]}
      />
    </View>
  );
}

export default function Login() {
  const router = useRouter();
  const { signIn } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    if (submitting) return;
    setError(null);
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setSubmitting(true);
    try {
      const me = await signIn(email, password);
      router.replace(isAdminRole(me.role) ? '/admin/dashboard' : '/home');
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Could not sign in. Check your connection.';
      setError(msg);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen paddingX={24}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 9 }}>
          <LinearGradient colors={gradients.avatar} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ width: 34, height: 34, borderRadius: 10 }} />
          <Text style={[font(800), { fontSize: 20, color: palette.ink, letterSpacing: -0.5 }]}>Teamora</Text>
        </View>
        <Pressable onPress={() => router.back()} accessibilityRole="button" accessibilityLabel="Back" hitSlop={8}>
          <Text style={[font(600), { fontSize: 13, color: palette.faint }]}>Back</Text>
        </Pressable>
      </View>

      <View style={{ marginTop: 36 }}>
        <Text style={[font(800), { fontSize: 28, color: palette.ink, letterSpacing: -0.8 }]}>Welcome back 👋</Text>
        <Text style={[font(500), { fontSize: 13.5, lineHeight: 20, color: palette.soft, marginTop: 8 }]}>
          Sign in to clock in, check your payslip and stay on top of your day.
        </Text>
      </View>

      <View style={{ marginTop: 26, gap: 12 }}>
        <Field icon="mail" placeholder="Work email" value={email} onChangeText={setEmail} keyboardType="email-address" />
        <Field icon="lock" placeholder="Password" secure value={password} onChangeText={setPassword} />
        {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}
        <Pressable
          accessibilityRole="button"
          hitSlop={10}
          onPress={() => {
            // The code-based reset needs an email provider, which isn't wired up
            // yet — so real users are pointed at their HR admin, and the flow is
            // only reachable in dev/preview builds (where the code is shown).
            if (showInternalHints()) {
              router.push('/forgot-password');
              return;
            }
            Alert.alert(
              'Forgot your password?',
              'Ask your HR admin to reset your password. They can set a new one from your employee page.',
            );
          }}
          style={{ alignSelf: 'flex-end', marginTop: 2 }}
        >
          <Text style={[font(600), { fontSize: 12.5, color: palette.coral }]}>Forgot password?</Text>
        </Pressable>
      </View>

      <View style={{ marginTop: 20 }}>
        <Button label={submitting ? 'Signing in…' : 'Sign in'} icon="arrowR" iconTrailing height={54} disabled={submitting} onPress={onSubmit} />
      </View>

      <Pressable onPress={() => router.push('/setup')} accessibilityRole="button" style={{ marginTop: 18, alignItems: 'center' }}>
        <Text style={[font(600), { fontSize: 13, color: palette.soft }]}>
          New company? <Text style={[font(700), { color: palette.coral }]}>Create an account</Text>
        </Text>
      </Pressable>

      <View style={{ marginTop: 'auto', paddingTop: 24, alignItems: 'center' }}>
        {/* Demo logins are an internal aid — never rendered in a production build. */}
        {showInternalHints() && (
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, textAlign: 'center', marginBottom: 6 }]}>
            Demo — staff: amir@lumi.com · admin: sarah@lumi.com (password: password).
          </Text>
        )}
        <Text style={[font(500), { fontSize: 11, lineHeight: 16, color: palette.faint, textAlign: 'center' }]}>
          By continuing you agree to Teamora's{' '}
          <Text accessibilityRole="link" onPress={() => router.push('/terms')} style={[font(700), { color: palette.soft, textDecorationLine: 'underline' }]}>
            Terms
          </Text>
          {' & '}
          <Text accessibilityRole="link" onPress={() => router.push('/privacy')} style={[font(700), { color: palette.soft, textDecorationLine: 'underline' }]}>
            Privacy
          </Text>
          .
        </Text>
      </View>
    </Screen>
  );
}
