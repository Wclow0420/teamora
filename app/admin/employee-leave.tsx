import React from 'react';
import { View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, EmptyState, ScreenHeader } from '@/components/ui';
import { LeaveEntitlementFields } from '@/components/LeaveEntitlementFields';
import { useAdminLeaveBalances, useEmployee } from '@/api/queries';
import { palette, spacing, tint } from '@/theme';

/**
 * Employee hub → **Leave entitlement**. One editable full-year entitlement per
 * leave type, saved through the admin override endpoint by
 * `LeaveEntitlementFields` (it owns its own Save button, since each row is a
 * separate override call — the employee record itself is never PATCHed here).
 */
export default function EmployeeLeave() {
  const router = useRouter();
  const params = useLocalSearchParams<{ id: string; name?: string }>();
  const detail = useEmployee(params.id);
  // Same query key as the one inside LeaveEntitlementFields — shared cache; read
  // here only to gate on loading / empty honestly.
  const balances = useAdminLeaveBalances(params.id);

  return (
    <Screen>
      <ScreenHeader back title="Leave entitlement" subtitle={detail.data?.fullName ?? params.name} />

      <AsyncBoundary loading={balances.isLoading} error={balances.error} onRetry={balances.refetch}>
        {(balances.data ?? []).length === 0 ? (
          <EmptyState
            icon="sun"
            title="No leave types yet"
            subtitle="Set up your company's leave types first, then give this employee their entitlement."
            tone={{ color: palette.sage, bg: tint.sage }}
            action={{ label: 'Leave types', icon: 'gear', onPress: () => router.push('/admin/company-settings') }}
          />
        ) : (
          <View style={{ gap: 14 }}>
            <LeaveEntitlementFields employeeId={params.id} />
            <View style={{ marginTop: spacing.sm }}>
              <Button label="Done" variant="light" icon="check" onPress={() => router.back()} />
            </View>
          </View>
        )}
      </AsyncBoundary>
    </Screen>
  );
}
