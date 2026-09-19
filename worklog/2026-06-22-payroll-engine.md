# 2026-06-22 — Real payroll engine (run → approve → pay)

## Why
Payslips only existed via the seeder — there was no way for HR to actually *run*
payroll, and the figures, while plausible, weren't computed from the company's
real data. This builds a real payroll run: generate payslips for a period from
each employee's salary + their approved overtime + approved claims, with correct
Malaysian statutory deductions, then drive an approve → paid lifecycle.

## Scope decision (v1)
- **In:** monthly salary per employee; a unit-tested statutory calculator
  (EPF/SOCSO/EIS); a real run that pulls approved OT + approved claims for the
  period; run/approve/mark-paid lifecycle; admin UI + salary fields.
- **Out (documented, not faked):** **PCB/MTD income tax** — it needs each
  employee's tax profile (marital status, children, reliefs) which the app
  doesn't model, so net pay excludes it. Stated in the calculator's Javadoc and
  CLAUDE.md §12 rather than fabricating a tax number.

## Backend
- **Migration `V9__employee_monthly_salary.sql`** — `employees.monthly_salary
  NUMERIC(12,2) NULL` (null = unset → that employee is skipped by a run).
  `Employee.monthlySalary`; exposed on `EmployeeResponse`; settable via the
  create/update DTOs + service + the demo seeder (salaries match the seeded June
  basics so a run reproduces them).
- **`PayrollCalculator`** (pure, deterministic, unit-tested to the sen):
  - Wage bases — EPF on basic+bonus; SOCSO/EIS on basic only; **overtime excluded
    from all statutory bases**, **claims never a base**.
  - EPF 11% employee / 12–13% employer, rounded **up to the ringgit**.
  - SOCSO 0.5%/1.75% + EIS 0.2%/0.2% on the **RM100 wage-band midpoint** capped at
    the RM6,000 ceiling (top band RM5,950) — the PERKESO table method at gazetted
    rates. (Verified it reproduces the seeded demo figures: RM4,000 → EPF 440,
    SOCSO 19.75, EIS 7.90.)
- **`PayrollService` run engine** + `PayrollController` (OWNER/HR_ADMIN only —
  MANAGER excluded via `@PreAuthorize`):
  - `POST /api/admin/payroll/run {period}` — generate/refresh DRAFT payslips:
    basic = salary; OT pay = approved OT hours in the period × EA hourly rate
    (monthly/26/8 × 1.5); claims = approved claims in the period. Idempotent;
    **never clobbers an APPROVED/PAID payslip**; reports `skippedCount` (no salary).
  - `GET /api/admin/payroll/run?period=` — current run state (`generated:false` if none).
  - `POST …/{period}/approve` (DRAFT→APPROVED) · `POST …/{period}/mark-paid`
    (APPROVED→PAID, guarded so you can't pay before approving).
- New repo finder: `OvertimeRepository.sumHoursInPeriod`. Run-level status =
  least-advanced payslip stage.

## App
- `src/api`: `monthlySalary` on `EmployeeResponse`/create/update bodies;
  `PayslipStatus` union + `PayrollRun`/`PayrollRunLine` types; `payrollApi`
  run/runView/approve/markPaid; `usePayrollRun` + `useRunPayroll`/
  `useApprovePayrollRun`/`useMarkPayrollPaid` (invalidate `['payroll']` + `['dashboard']`).
- `app/admin/(tabs)/payroll.tsx` — rebuilt around the run: honest empty state +
  **Run payroll**; once generated, a net hero + gross/statutory/net totals +
  per-employee breakdown + a status-driven action (**Approve** → **Mark as paid**,
  both with confirm dialogs; a **Paid** done state); a gentle note when employees
  are skipped for missing salaries.
- `app/admin/employee-new.tsx` / `employee-edit.tsx` — "Monthly salary (RM)" field
  (decimal pad, safe parse, prefilled on edit); `staff.tsx` passes it through.

## Verification
- Backend test gate: **`./scripts/test-backend.sh` → BUILD SUCCESS, 43 tests**
  (new `PayrollCalculatorTest` — 8 exact-sen unit cases; `PayrollRunIT` — run,
  idempotent re-run, can't-pay-before-approve, approve, mark-paid, MANAGER 403,
  EMPLOYEE 403, unauth 401; nothing else broke).
- App: `npm run typecheck` clean.
- Live (dev DB reset → V9 applied + salaries seeded): ran June payroll as
  HR_ADMIN → **10 payslips, gross RM61,308 → net RM54,226**, real per-employee
  figures (Amir RM4,000 + RM108 approved claims → net RM3,640.35; Wei Jie RM7,000
  → RM6,188.35, matching the unit tests); DRAFT→APPROVED→PAID worked; MANAGER/
  EMPLOYEE 403, unauth 401. Salary PATCH persists; the Dashboard payroll-due KPI
  now reads the real approved run.

## Follow-ups
- **PCB/MTD**: model an employee tax profile + the MTD computation to make net pay
  complete. Largest remaining payroll gap.
- Employer statutory cost (employer EPF/SOCSO/EIS) is computed in the calculator
  but not yet surfaced — could show "cost to company" on the run.
- Bonus/allowance inputs per run; payslip PDF/email; pay-date convention
  (currently last day of month).
