import React, { useState } from 'react';
import { View } from 'react-native';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Chip } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import {
  ApprovalTabs,
  ClaimApprovalCard,
  EmptyApprovals,
  LeaveApprovalCard,
  OvertimeApprovalCard,
} from '@/components/approvals/ApprovalCards';
import { usePendingClaims, usePendingLeave, usePendingOvertime } from '@/api/queries';
import { useApprovalDecisions } from '@/hooks';
import { palette, tint } from '@/theme';

type Tab = 'leave' | 'claims' | 'ot';

export default function Approvals() {
  const [tab, setTab] = useState<Tab>('leave');
  const leave = usePendingLeave();
  const claims = usePendingClaims();
  const ot = usePendingOvertime();
  const decisions = useApprovalDecisions();

  const total = (leave.data?.length ?? 0) + (claims.data?.length ?? 0) + (ot.data?.length ?? 0);
  const tabs = [
    { key: 'leave' as Tab, label: 'Leave', count: leave.data?.length ?? 0 },
    { key: 'claims' as Tab, label: 'Claims', count: claims.data?.length ?? 0 },
    { key: 'ot' as Tab, label: 'OT', count: ot.data?.length ?? 0 },
  ];

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      large
      title="Approvals"
      subtitle="Leave, claims & overtime"
      accessory={<Chip label={`${total} pending`} background={tint.amber} color={palette.amber} />}
      headerExtra={
        <View style={{ marginTop: 14 }}>
          <ApprovalTabs tabs={tabs} value={tab} onChange={setTab} />
        </View>
      }
    >
      {tab === 'leave' && (
        <AsyncBoundary loading={leave.isLoading} error={leave.error} onRetry={leave.refetch}>
          {leave.data &&
            (leave.data.length === 0 ? (
              <EmptyApprovals label="No leave to review" />
            ) : (
              <View style={{ gap: 12 }}>
                {leave.data.map((p) => (
                  <LeaveApprovalCard key={p.id} item={p} pending={decisions.leavePending}
                    onApprove={() => decisions.decideLeave(p.id, 'approve')}
                    onReject={() => decisions.decideLeave(p.id, 'reject')} />
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
                  <ClaimApprovalCard key={c.id} item={c} pending={decisions.claimPending}
                    onApprove={() => decisions.decideClaim(c.id, 'approve')}
                    onReject={() => decisions.decideClaim(c.id, 'reject')} />
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
                  <OvertimeApprovalCard key={o.id} item={o} pending={decisions.overtimePending}
                    onApprove={() => decisions.decideOvertime(o.id, 'approve')}
                    onReject={() => decisions.decideOvertime(o.id, 'reject')} />
                ))}
              </View>
            ))}
        </AsyncBoundary>
      )}
    </CollapsingHeaderScreen>
  );
}
