import React from 'react';
import { Stack } from 'expo-router';
import { palette } from '@/theme';

/**
 * Staff stack: the tab bar lives in (tabs), while clock-in / leave / claims /
 * notifications are pushed on top (so the dark face-scan screen can go
 * full-bleed without the tab bar).
 */
export default function StaffLayout() {
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: palette.bg } }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="clock-in" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="leave" />
      <Stack.Screen name="leave-apply" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="claims" />
      <Stack.Screen name="claim-submit" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="notifications" />
      <Stack.Screen name="approvals" />
      <Stack.Screen name="overtime-submit" options={{ animation: 'slide_from_bottom' }} />
    </Stack>
  );
}
