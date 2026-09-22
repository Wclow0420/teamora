# 2026-09-19 — Configurable compensation + leave engine (backend)

Backend half of the configurable pay + leave feature. Admin enters a monthly
salary; the system derives daily/hourly rates from a per-staff (or company
default) work schedule, and leave types are now company-scoped rows that can be
paid or unpaid — unpaid leave deducts from pay at the derived daily rate.
Statutory (EPF/SOCSO/EIS/PCB) stays automatic and is now computed on the
**paid** basic so unpaid leave correctly lowers it. OT is untouched (still EA
statutory /26/8 ×1.5).

Frontend (`app/`, `src/`) is a separate agent's job and was not touched.

## Migration
`V11__configurable_comp_and_leave.sql`:
- `company_settings` (1:1 with companies) — `default_pay_basis`,
  `default_working_days` (weekday bitmask), `default_hours_per_day`; seeded per
  existing company (Mon–Fri, 8h, MONTHLY).
- `employees` — nullable `pay_basis`, `working_days` (SMALLINT), `hours_per_day`
  overrides (null ⇒ inherit company default).
- `leave_types` (company-scoped) — replaces the hardcoded `LeaveType` enum;
  stable `code` + name/paid/entitlement/accrual/colorKey/active/sortOrder;
  seeded Annual/Medical/Emergency (paid) + Unpaid Leave (unpaid) per company.
- `leave_requests` / `leave_balances` — converted `leave_type` VARCHAR → a
  `leave_type_id` FK (backfilled by matching code within the same company),
  NOT NULL, old columns dropped, balances uniqueness swapped to
  `(employee_id, leave_type_id)`.
- `payslips` — `unpaid_days`, `unpaid_deduction`, `paid_days`, `daily_rate`.

On a fresh DB the per-company seeds/backfills touch zero rows (companies are
created by the runtime seeders); the seeders create the settings + leave types
(`EmployeeSeeder` for demo tenants, `AuthService.register` for new signups).

## Key classes
- `common/WorkWeek` — bitmask helpers (bit0=Mon..bit6=Sun; count scheduled
  days in a month).
- `employee/PayBasis` (MONTHLY|DAILY|HOURLY); `leave/LeaveAccrual`.
- `company/CompanySettings` (+repo/service/controller/DTOs) — `/api/admin/company-settings`.
- `leave/LeaveType` is now a JPA entity (+`LeaveTypeRepository`,
  `LeaveTypeService` CRUD+seed, `LeaveTypeController`).
- `payroll/CompensationService` — resolves the effective schedule, derives
  daily/hourly for a `YearMonth`, and computes the paid basic per pay basis
  from APPROVED leave (unpaid types), public holidays (`company_events`
  HOLIDAY) and attendance. Wired into `PayrollService.runPayroll`.

## Notes / deviations
- `working_days` columns are SMALLINT mapped to Java `short`/`Short` (not
  `Integer`) so Hibernate `ddl-auto: validate` matches the int2 column type; the
  API still exposes/accepts `int`.
- Pre-existing bug fixed to get the gate green: the admin Dashboard read
  transaction was poisoned when the current month had no payroll run
  (`payrollService.summary` threw `ResourceNotFoundException` inside the shared
  tx → `UnexpectedRollbackException`; `DashboardIT` was red on `main` at the
  current date). Added a non-throwing `PayrollService.netLabelForDashboard` and
  used it from `DashboardService`.
- Seeded leave stays PAID and no unpaid usage is seeded, so existing payroll
  numbers are unchanged (Amir basic 4,000.00; `PayrollRunIT` green).

## API added/changed (response field names for the app)
- `GET/PATCH /api/admin/company-settings` → `{ defaultPayBasis, defaultWorkingDays, defaultHoursPerDay }`.
- `GET /api/leave/types` (staff, active only) / `GET /api/admin/leave/types`
  (all) / `POST` / `PATCH /api/admin/leave/types/{id}` →
  `{ id, name, code, paid, defaultEntitlementDays, accrual, colorKey, active, sortOrder }`.
- `POST /api/leave/requests` body now `{ leaveTypeId, startDate, endDate, reason? }`.
- `GET /api/leave/balances` items → `{ leaveTypeId, code, name, colorKey, paid, used, entitled, remaining }`.
- Leave request rows → `{ id, leaveTypeId, typeCode, typeLabel, colorKey, paid, dateRangeLabel, durationLabel, reason, status, statusLabel }`.
- Employee create/update accept `payBasis, workingDays, hoursPerDay`; employee
  detail response adds `payBasis, workingDays, hoursPerDay, derivedDailyRate, derivedHourlyRate`.
- Payslip response adds `unpaidDays, unpaidDaysLabel, unpaidDeductionLabel, dailyRateLabel`.

## Tests
`./scripts/test-backend.sh` green — 13 + 6 unit, 50 IT, 0 failures.
New: `CompensationServiceTest`, `CompLeavePayrollIT`, `LeaveTypeConfigIT`.
Updated the leave-posting ITs (`LeaveFlowIT`, `NotificationFlowIT`,
`ApprovalRoutingIT`) to the `{ leaveTypeId }` body via a shared
`leaveTypeId(token, code)` helper.

## v2 follow-ups (non-goals now)
Carry-forward, join-date pro-ration, tenure accrual, bonus source, writing
ON_LEAVE attendance rows, multi-country statutory.
