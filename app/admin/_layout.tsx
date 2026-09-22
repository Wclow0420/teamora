import React from 'react';
import { Stack } from 'expo-router';
import { palette } from '@/theme';

/**
 * Admin stack: the tab bar lives in (tabs), while Live attendance and Scheduling
 * are pushed on top (reached from the dashboard's quick links) — full-screen
 * with a back button and no tab bar.
 */
export default function AdminLayout() {
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: palette.bg } }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="live" />
      <Stack.Screen name="schedule" />
      <Stack.Screen name="employee-new" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="shift-assign" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="employee-edit" />
      <Stack.Screen name="company-settings" />
      <Stack.Screen name="payroll-export" />
      <Stack.Screen name="leave-type-edit" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="work-locations" />
      <Stack.Screen name="work-location-edit" options={{ animation: 'slide_from_bottom' }} />
      <Stack.Screen name="calendar-events" />
      <Stack.Screen name="calendar-event-edit" options={{ animation: 'slide_from_bottom' }} />
    </Stack>
  );
}
