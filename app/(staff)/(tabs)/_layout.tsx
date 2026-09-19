import React from 'react';
import { Tabs } from 'expo-router';
import { PillTabBar } from '@/components/navigation/PillTabBar';
import type { IconName } from '@/components/ui';

/**
 * Staff bottom navigation — the floating pill nav. Each screen declares its
 * icon via the custom `tabBarIconName` option that PillTabBar reads.
 */
export default function StaffTabsLayout() {
  return (
    <Tabs tabBar={(props) => <PillTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="home" options={{ title: 'Home', tabBarIconName: 'home' as IconName } as any} />
      <Tabs.Screen name="attendance" options={{ title: 'Attendance', tabBarIconName: 'clock' as IconName } as any} />
      <Tabs.Screen name="calendar" options={{ title: 'Calendar', tabBarIconName: 'calendar' as IconName } as any} />
      <Tabs.Screen name="payroll" options={{ title: 'Payroll', tabBarIconName: 'wallet' as IconName } as any} />
      <Tabs.Screen name="profile" options={{ title: 'Profile', tabBarIconName: 'user' as IconName } as any} />
    </Tabs>
  );
}
