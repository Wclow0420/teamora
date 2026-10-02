# 2026-10-02 — QA round 2 (backend): honest data, leave guards, configurable lateness

Backend half of the round-2 QA fixes. Migration **V18**; test gate green at **198** (43 unit + 155 IT; was 185).

## What changed
- **Payslip bank line is real.** `PayslipResponse.bankLabel` is built from the employee's `bankName` + last 4 of
  `bankAccountNo` (`"Maybank ••1234"`), bank name only when there's no account number, `null` when neither is set.
  The hard-coded "Maybank ••4821" is gone.
- **Staff never see a non-final payslip.** `GET /api/payroll/payslips[/{period}]` return only APPROVED / PAID
  (a DRAFT — and the seed-only IN_REVIEW — is hidden; 404 by period).
- **Admin run line** gains `unpaidDays`, `unpaidDaysLabel`, `unpaidDeductionLabel` (null when nothing was deducted).
- **No fake location.** Clock-in without an assigned active site stores `location = null` (was "Bangsar South HQ").
  `EmployeeResponse.location` is now the assigned `WorkLocation` name or null — it used to echo the legacy
  `employees.location` column, which create/register/seed back-filled with the company name/address. That column is
  no longer written or exposed. `LiveStaffRow` gains `location` (the site clocked in at, or null). Seeders no longer
  write site strings (no work locations are seeded). V18 nulls old `'Bangsar South HQ'` attendance rows where the
  company has no site of that name.
- **Lateness is configurable.** `company_settings.work_start_time` (default 09:00) + `late_grace_minutes`
  (default 5, 0–120), exposed as `workStartTime: "HH:mm"` / `lateGraceMinutes` on `GET/PATCH /api/admin/company-settings`.
  `AttendanceService` uses them instead of the fixed 09:05.
- **Leave apply guards (400).** Overlap with a PENDING/APPROVED request ("You already have a leave request covering
  these dates."): a full day clashes with anything on the same date; AM+PM half days and hourly requests coexist.
  Over balance for tracked types (accrual != NONE): requested > available − already-pending for that type + leave year
  ("Not enough {type} balance — you have {n} day(s) available."). Untracked types are never blocked.
- **Validation (400).** Working-days mask 0 (company settings, employee create/update); blank company name on
  `PATCH /api/companies/me`; passwords min 8 on register + create employee (change/reset already enforced it).
- **Staff ID / phone.** Trimmed on create/update, blank clears, max 32 chars; staff id is now unique **per company**
  (V18 swaps the global unique constraint for `(company_id, staff_id)`), duplicate → 400.
- **Join-date change re-prorates** the employee's current-leave-year balances (`LeaveBalanceService.reprorateForJoinDateChange`).
  Overrides are never clobbered: `leave_balances.entitlement_overridden` (V18, set by the admin override endpoint), plus a
  fallback for older rows — a row whose `entitled` no longer equals the proration formula for the *previous* join date is
  treated as hand-set and left alone. Never drops below days already used.

## Non-obvious / follow-ups
- The demo seed's June payslips are `IN_REVIEW`, so on a fresh seed staff see no payslip until an admin approves the run.
- The over-balance check uses "available today"; a MONTHLY_ACCRUAL type booked far ahead is checked against what has
  accrued so far (same number the app shows).
- `TodayStatusResponse.shift` is still the hard-coded "9:00 AM – 6:00 PM" — not in this round's scope; should follow
  `workStartTime` / the schedule next.
- Tests: new `qa/QaRound2ContractIT` (13 tests); `CompLeavePayrollIT` / `PartialLeavePayrollIT` now approve the run
  before reading the staff payslip (and assert the draft is hidden).
