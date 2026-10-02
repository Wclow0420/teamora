import { useCalendar, useLeaveBalances, useLeaveRequests } from '@/api/queries';
import type { LeaveBalance, LeaveDurationUnit, MonthCalendar } from '@/api/types';
import { monthKey, workingDaysInRange } from '@/lib/leaveDays';

/** Accent key the calendar uses for public-holiday dots (backend `EventType.HOLIDAY`). */
const HOLIDAY_ACCENT = 'amber';

type Input = {
  leaveTypeId: string;
  unit: LeaveDurationUnit;
  start: Date;
  end: Date;
  /** Parsed hours for an HOURS request (null when blank/invalid). */
  hours: number | null;
  hoursPerDay: number;
  /** The employee's working-weekday bitmask (undefined until the profile loads). */
  workingDaysMask: number | undefined;
};

export type LeaveBalanceCheck = {
  /** The chosen type's balance — null for untracked types (unpaid etc.) or while loading. */
  balance: LeaveBalance | null;
  /** How many days this request would use, when it can be worked out locally. */
  requestedDays: number | null;
  /** True only when we're sure the request is bigger than the balance. */
  exceeds: boolean;
  /** The employee already has a pending request of this type (it also counts against the balance). */
  hasPending: boolean;
};

function addHolidays(into: Set<string>, month: string, cal: MonthCalendar | undefined) {
  if (!cal) return;
  for (const [day, accents] of Object.entries(cal.events ?? {})) {
    if (accents.includes(HOLIDAY_ACCENT)) into.add(`${month}-${day.padStart(2, '0')}`);
  }
}

/**
 * Pre-checks a leave request against the employee's real balance so the form can
 * say "not enough balance" before submitting. Deliberately conservative: it only
 * reports `exceeds` when the local count is exact (same rules as the server —
 * rest days and public holidays skipped — and the request sits in the leave year
 * the balance belongs to). Anything it can't be sure of is left to the server,
 * whose message the form shows as-is.
 */
export function useLeaveBalanceCheck({ leaveTypeId, unit, start, end, hours, hoursPerDay, workingDaysMask }: Input): LeaveBalanceCheck {
  const balances = useLeaveBalances();
  const requests = useLeaveRequests();
  const startMonth = monthKey(start);
  const endMonth = monthKey(unit === 'FULL_DAY' ? end : start);
  const startCal = useCalendar(startMonth);
  const endCal = useCalendar(endMonth);

  const row = balances.data?.find((b) => b.leaveTypeId === leaveTypeId) ?? null;
  const balance = row && row.accrual !== 'NONE' ? row : null;

  const hasPending =
    requests.data?.some((r) => r.leaveTypeId === leaveTypeId && r.status.toUpperCase() === 'PENDING') ?? false;

  let requestedDays: number | null = null;
  if (unit === 'HALF_DAY') {
    requestedDays = 0.5;
  } else if (unit === 'HOURS') {
    if (hours != null && hours > 0 && hoursPerDay > 0) requestedDays = Math.round((hours / hoursPerDay) * 100) / 100;
  } else if (workingDaysMask != null && startCal.data && endCal.data) {
    // Only count when every month the range touches has its holidays loaded.
    const next = new Date(start.getFullYear(), start.getMonth() + 1, 1);
    const spansLoadedMonths = startMonth === endMonth || monthKey(next) === endMonth;
    if (spansLoadedMonths) {
      const holidays = new Set<string>();
      addHolidays(holidays, startMonth, startCal.data);
      addHolidays(holidays, endMonth, endCal.data);
      requestedDays = workingDaysInRange(start, end, workingDaysMask, holidays);
    }
  }

  // The balance on hand is for the current leave year. We can only be sure the
  // request falls in it when today, the start date and the leave year all share
  // one calendar year (a leave year always runs to at least 31 Dec of its start year).
  const thisYear = new Date().getFullYear();
  const sameLeaveYear = balance != null && balance.leaveYear === thisYear && start.getFullYear() === thisYear;

  const exceeds =
    balance != null && sameLeaveYear && requestedDays != null && requestedDays > balance.remaining + 1e-9;

  return { balance, requestedDays, exceeds, hasPending };
}
