import React from 'react';
import { Tabs } from 'expo-router';
import { PillTabBar } from '@/components/navigation/PillTabBar';
import type { IconName } from '@/components/ui';

/**
 * Admin bottom navigation — the floating pill nav. Tabs: Home · Staff · Approve
 * · Payroll · Profile. Live attendance and Scheduling are pushed detail screens
 * (in the parent stack), reached from the dashboard.
 */
export default function AdminTabsLayout() {
  return (
    <Tabs tabBar={(props) => <PillTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="dashboard" options={{ title: 'Home', tabBarIconName: 'grid' as IconName } as any} />
      <Tabs.Screen name="staff" options={{ title: 'Staff', tabBarIconName: 'users' as IconName } as any} />
      <Tabs.Screen name="approvals" options={{ title: 'Approve', tabBarIconName: 'check' as IconName } as any} />
      <Tabs.Screen name="payroll" options={{ title: 'Payroll', tabBarIconName: 'wallet' as IconName } as any} />
      <Tabs.Screen name="profile" options={{ title: 'Profile', tabBarIconName: 'user' as IconName } as any} />
    </Tabs>
  );
}
