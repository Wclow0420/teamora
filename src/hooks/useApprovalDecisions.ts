import { Alert } from 'react-native';
import { useDecideClaim, useDecideLeave, useDecideOvertime } from '@/api/queries';
import { alertError } from '@/lib/errors';

type Decision = 'approve' | 'reject';

/**
 * Approve / decline actions for the three approval queues, shared by the
 * manager inbox and the admin Approvals tab. A failed decision (e.g. 403 "not
 * the approver", or the request was already decided elsewhere) is shown in an
 * alert with the server's reason — it never fails silently.
 */
export function useApprovalDecisions() {
  const leave = useDecideLeave();
  const claim = useDecideClaim();
  const overtime = useDecideOvertime();

  const onError = (decision: Decision) => (e: unknown) =>
    alertError(decision === 'approve' ? "Couldn't approve" : "Couldn't decline", e);

  /**
   * Declining can't be undone and notifies the employee, and the button sits
   * right beside Approve — so it asks first. Approving stays one tap.
   */
  const confirmed = (decision: Decision, what: string, run: () => void) => {
    if (decision === 'approve') return run();
    Alert.alert(`Decline this ${what}?`, "They'll be notified, and this can't be undone.", [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Decline', style: 'destructive', onPress: run },
    ]);
  };

  return {
    leavePending: leave.isPending,
    claimPending: claim.isPending,
    overtimePending: overtime.isPending,
    decideLeave: (id: string, decision: Decision) =>
      confirmed(decision, 'leave request', () => leave.mutate({ id, decision }, { onError: onError(decision) })),
    decideClaim: (id: string, decision: Decision) =>
      confirmed(decision, 'claim', () => claim.mutate({ id, decision }, { onError: onError(decision) })),
    decideOvertime: (id: string, decision: Decision) =>
      confirmed(decision, 'overtime request', () =>
        overtime.mutate({ id, decision }, { onError: onError(decision) }),
      ),
  };
}
