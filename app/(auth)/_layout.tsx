import React from 'react';
import { Stack } from 'expo-router';
import { palette } from '@/theme';

export default function AuthLayout() {
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: palette.bg } }}>
      <Stack.Screen name="onboarding" />
      <Stack.Screen name="login" />
      <Stack.Screen name="register" />
      {/* Setup wizard: Back is the wizard's own (step by step); a swipe must not drop out mid-flow. */}
      <Stack.Screen name="setup" options={{ gestureEnabled: false }} />
      <Stack.Screen name="forgot-password" />
      {/* legal — also pushed from both Profile screens while signed in */}
      <Stack.Screen name="privacy" />
      <Stack.Screen name="terms" />
    </Stack>
  );
}
