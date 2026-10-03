import { useState } from 'react';
import { useDecideClaim, useDecideLeave, useDecideOvertime } from '@/api/queries';
import { alertError, errorMessage } from '@/lib/errors';

type Decision = 'approve' | 'reject';
type Kind = 'leave' | 'claim' | 'overtime';

const WHAT: Record<Kind, string> = {
  leave: 'leave request',
  claim: 'claim',
  overtime: 'overtime request',
};

/**
 * Approve / decline actions for the three approval queues, shared by the
 * manager inbox and the admin Approvals tab.
 *
 * Approving stays one tap; a failure (e.g. 403 "not the approver", or already
 * decided elsewhere) is shown in an alert with the server's reason.
 *
 * Declining can't be undone, notifies the employee, and its button sits right
 * beside Approve — so `decideX(id, 'reject')` opens a confirm sheet (render
 * `<DeclineSheet {...decisions.declineSheet} />` once per screen) where the
 * approver can add an optional reason. The sheet stays open while the request
 * runs and shows a failure inline, so they can retry or cancel.
 */
export function useApprovalDecisions() {
  const leave = useDecideLeave();
  const claim = useDecideClaim();
  const overtime = useDecideOvertime();
  const mutations = { leave, claim, overtime };

  const [target, setTarget] = useState<{ kind: Kind; id: string } | null>(null);
  // Kept after close so the sheet's title doesn't change mid slide-out.
  const [lastKind, setLastKind] = useState<Kind>('leave');
  const [declineError, setDeclineError] = useState<string | null>(null);

  const decide = (kind: Kind, id: string, decision: Decision) => {
    if (decision === 'reject') {
      setDeclineError(null);
      setLastKind(kind);
      setTarget({ kind, id });
      return;
    }
    mutations[kind].mutate({ id, decision }, { onError: (e) => alertError("Couldn't approve", e) });
  };

  const confirmDecline = (reason: string) => {
    if (!target) return;
    setDeclineError(null);
    mutations[target.kind].mutate(
      { id: target.id, decision: 'reject', reason: reason || undefined },
      {
        onSuccess: () => setTarget(null),
        onError: (e) => setDeclineError(errorMessage(e)),
      },
    );
  };

  return {
    leavePending: leave.isPending,
    claimPending: claim.isPending,
    overtimePending: overtime.isPending,
    decideLeave: (id: string, decision: Decision) => decide('leave', id, decision),
    decideClaim: (id: string, decision: Decision) => decide('claim', id, decision),
    decideOvertime: (id: string, decision: Decision) => decide('overtime', id, decision),
    /** Props for the screen's single `<DeclineSheet />`. */
    declineSheet: {
      visible: target !== null,
      what: WHAT[lastKind],
      pending: target ? mutations[target.kind].isPending : false,
      error: declineError,
      onCancel: () => setTarget(null),
      onConfirm: confirmDecline,
    },
  };
}
