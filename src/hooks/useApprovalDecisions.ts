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

  return {
    leavePending: leave.isPending,
    claimPending: claim.isPending,
    overtimePending: overtime.isPending,
    decideLeave: (id: string, decision: Decision) => leave.mutate({ id, decision }, { onError: onError(decision) }),
    decideClaim: (id: string, decision: Decision) => claim.mutate({ id, decision }, { onError: onError(decision) }),
    decideOvertime: (id: string, decision: Decision) =>
      overtime.mutate({ id, decision }, { onError: onError(decision) }),
  };
}
