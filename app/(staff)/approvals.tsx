import React, { useState } from 'react';
import { View } from 'react-native';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { ScreenHeader } from '@/components/ui';
import {
  ApprovalTabs,
  ClaimApprovalCard,
  EmptyApprovals,
  LeaveApprovalCard,
  OvertimeApprovalCard,
} from '@/components/approvals/ApprovalCards';
import {
  usePendingClaims,
  usePendingLeave,
  usePendingOvertime,
  useDecideClaim,
  useDecideLeave,
  useDecideOvertime,
} from '@/api/queries';

type Tab = 'leave' | 'claims' | 'ot';

/** Manager approvals inbox — pending leave, claims & overtime from direct reports. */
export default function StaffApprovals() {
  const [tab, setTab] = useState<Tab>('leave');
  const leave = usePendingLeave();
  const claims = usePendingClaims();
  const ot = usePendingOvertime();
  const decideLeave = useDecideLeave();
  const decideClaim = useDecideClaim();
  const decideOt = useDecideOvertime();

  const tabs = [
    { key: 'leave' as Tab, label: 'Leave', count: leave.data?.length ?? 0 },
    { key: 'claims' as Tab, label: 'Claims', count: claims.data?.length ?? 0 },
    { key: 'ot' as Tab, label: 'OT', count: ot.data?.length ?? 0 },
  ];

  return (
    <Screen>
      <ScreenHeader back title="Approvals" subtitle="Requests from your team" />

      <View style={{ marginBottom: 14 }}>
        <ApprovalTabs tabs={tabs} value={tab} onChange={setTab} />
      </View>

      {tab === 'leave' && (
        <AsyncBoundary loading={leave.isLoading} error={leave.error} onRetry={leave.refetch}>
          {leave.data &&
            (leave.data.length === 0 ? (
              <EmptyApprovals label="No leave to review" />
            ) : (
              <View style={{ gap: 12 }}>
                {leave.data.map((p) => (
                  <LeaveApprovalCard key={p.id} item={p} pending={decideLeave.isPending}
                    onApprove={() => decideLeave.mutateAsync({ id: p.id, decision: 'approve' })}
                    onReject={() => decideLeave.mutateAsync({ id: p.id, decision: 'reject' })} />
                ))}
              </View>
            ))}
        </AsyncBoundary>
      )}

      {tab === 'claims' && (
        <AsyncBoundary loading={claims.isLoading} error={claims.error} onRetry={claims.refetch}>
          {claims.data &&
            (claims.data.length === 0 ? (
              <EmptyApprovals label="No claims to review" />
            ) : (
              <View style={{ gap: 12 }}>
                {claims.data.map((c) => (
                  <ClaimApprovalCard key={c.id} item={c} pending={decideClaim.isPending}
                    onApprove={() => decideClaim.mutateAsync({ id: c.id, decision: 'approve' })}
                    onReject={() => decideClaim.mutateAsync({ id: c.id, decision: 'reject' })} />
                ))}
              </View>
            ))}
        </AsyncBoundary>
      )}

      {tab === 'ot' && (
        <AsyncBoundary loading={ot.isLoading} error={ot.error} onRetry={ot.refetch}>
          {ot.data &&
            (ot.data.length === 0 ? (
              <EmptyApprovals label="No overtime to review" />
            ) : (
              <View style={{ gap: 12 }}>
                {ot.data.map((o) => (
                  <OvertimeApprovalCard key={o.id} item={o} pending={decideOt.isPending}
                    onApprove={() => decideOt.mutateAsync({ id: o.id, decision: 'approve' })}
                    onReject={() => decideOt.mutateAsync({ id: o.id, decision: 'reject' })} />
                ))}
              </View>
            ))}
        </AsyncBoundary>
      )}
    </Screen>
  );
}
