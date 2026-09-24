# 2026-09-24 — Partial-day leave: half-day & hourly (backend)

Leave stops being whole-day. Staff can now take a **half day (AM/PM)** or a
number of **hours**, and every downstream number — the request's day count, the
balance deduction, and the payroll unpaid-leave deduction — is a **fraction of a
working day** carried to 2dp.

Shipped with it: a real **correctness fix**. Leave used to consume balance for
raw calendar days, so a Fri–Mon request ate 4 days of annual leave including the
weekend. Payroll already ignored rest days and public holidays when pricing
leave; the balance now agrees with it. This entry covers `backend/**` only (the
app side is a separate entry).

## What changed

### Migration
- `V15__partial_day_leave.sql` (V14 was the previous head)
  - `leave_requests.days` → `NUMERIC(5,2)`; adds `duration_unit VARCHAR(16) NOT
    NULL DEFAULT 'FULL_DAY'`, `half_day_period VARCHAR(2)`, `start_time TIME`,
    `hours NUMERIC(4,2)`.
  - `leave_balances.entitled` / `used` → `NUMERIC(6,2)`.
  - `payslips.unpaid_days` → `NUMERIC(5,2)`.
  - `INTEGER → NUMERIC` conversions are value-preserving and the `duration_unit`
    default keeps every historic row valid. Verified against the **real dev
    database** inside a rolled-back transaction (6 requests / 3 balances / 20
    payslips converted cleanly; dev data untouched).

### New
- `leave/LeaveDurationUnit` — `FULL_DAY | HALF_DAY | HOURS`.
- `leave/HalfDayPeriod` — `AM | PM`.
- `leave/LeaveDurationCalculator` — **the single source of truth for "how many
  days is this leave?"**. It resolves the work week and hours/day through
  `CompensationService.resolveSchedule(employee)` (deliberately the *same*
  resolution payroll uses, rather than a second copy) and skips `HOLIDAY`
  company events via `CompanyEventRepository`.
  - `FULL_DAY` → 1.00 per scheduled working day in the range, rest days and
    public holidays skipped; **zero working days → 400**.
  - `HALF_DAY` → 0.50, single date only, must be a working day, AM/PM required.
  - `HOURS` → `hours / hoursPerDay` at 2dp HALF_UP, single date, `0 < hours <=
    hoursPerDay`.
  - Also exposes `static hourFraction(hours, hoursPerDay)` so payroll prices an
    hourly leave with the exact same arithmetic the request was booked with.
  - Every rejection is a `BadRequestException` with a sentence a real employee
    can act on (e.g. "6 Mar 2027 isn't one of your working days", "That's more
    than a working day (8 hours) — apply for a full day instead").
  - Defensive cap: a single request can't exceed 366 days.

### Changed
- `LeaveRequest` — `days` → `BigDecimal(5,2)`; new `durationUnit` (defaults
  `FULL_DAY`), `halfDayPeriod`, `startTime`, `hours`.
- `LeaveBalance` — `entitled`/`used` → `BigDecimal(6,2)`; `remaining()` returns
  `BigDecimal`; added `entitledOrZero()` / `usedOrZero()` (null-safe, 2dp).
- `LeaveService.apply` — `inclusiveDays()` deleted; delegates to the calculator
  and persists the unit/period/hours/startTime. The approver notification now
  reads the rendered duration label ("Half day (AM)") instead of "N days".
- `LeaveService.approve` — balance `used` accumulates in `BigDecimal`.
- `LeaveSeeder` — BigDecimal balances/days. Demo requests are weekday-only full
  days, so their counts are unchanged.
- `CompensationService.forPeriod` — **the money-critical change.** The two
  `Set<LocalDate>` whole-day counts became `Map<LocalDate, BigDecimal>` fraction
  accumulators (`FULL_DAY` 1.00 / `HALF_DAY` 0.50 / `HOURS` hours÷hoursPerDay).
  The existing rest-day and public-holiday skips are untouched. A date is capped
  at 1.00 however many requests overlap it, which is exactly what the old Set
  dedupe did. Then: MONTHLY `unpaidDeduction = dailyRate × unpaidFraction`;
  DAILY adds the paid-leave fraction to paid days; HOURLY adds
  `paidLeaveFraction × hoursPerDay`. Money still rounds once, at 2dp HALF_UP,
  after the fraction multiply — so a half day of an RM200.00/day salary is
  RM100.00 to the sen.
- `Compensation.unpaidDays` → `BigDecimal`. `paidDays` stays `Integer` (the
  `payslips.paid_days` column is `INT` and it is informational only — the exact
  money comes from the fraction, not from this figure); it's rounded HALF_UP and
  commented as such.
- `Payslip.unpaidDays` → `BigDecimal(5,2)`; `PayrollService` passes it straight
  through.
- `PayslipFormat.days(BigDecimal)` — new; renders a day count without trailing
  zeros (2.00 → "2", 0.50 → "0.5"). Used by the payslip DTO and the payroll CSV.
- `LeaveLabels.duration(days, unit, period, hours)` — renders "Half day (AM)",
  "2 hours", "1 day", "3 days". Plus `hoursLabel` / `dayLabel` / `plain` helpers.

### API shapes
`POST /api/leave/requests` — `ApplyLeaveRequest` gains four optional fields:
```jsonc
{
  "leaveTypeId": "uuid", "startDate": "2027-03-01", "endDate": "2027-03-01",
  "durationUnit": "FULL_DAY | HALF_DAY | HOURS",  // optional, defaults FULL_DAY
  "halfDayPeriod": "AM | PM",                      // HALF_DAY only
  "hours": 2,                                      // HOURS only
  "startTime": "14:00",                            // HOURS only, informational
  "reason": "…"
}
```
Omitting `durationUnit` behaves exactly as before, so existing clients keep
working (covered by `LeaveFlowIT`).

`LeaveRequestResponse` / `PendingLeaveResponse` keep every existing field and add
`durationUnit`, `halfDayPeriod` (nullable), `hoursLabel` (nullable) and `days`
(decimal) next to the existing `durationLabel`. `PendingLeaveResponse.balanceLabel`
now renders fractions ("12.5 days left").

`LeaveBalanceResponse` keeps `used` / `entitled` / `remaining` as JSON **numbers**
(now 2dp decimals, e.g. `4.50`) and adds pre-rendered `usedLabel` ("4.5 / 16")
and `remainingLabel` ("11.5 days left").

`PayslipResponse.unpaidDays` is now a decimal number; `unpaidDaysLabel` renders
"None" / "0.5 days" / "2 days".

## Existing payroll figures: unchanged

Deliberately verified, since this is money. Every pinned figure in
`PayrollRunIT` (Amir RM4,000.00 basic / EPF 440.00 / PCB 30.00 / net 3,502.35),
`CompLeavePayrollIT` (RM3,600.00 basic, RM400.00 deduction, RM200.00/day) and
`PayrollExportIT` (the seeded June run) passes untouched. That is expected: for
whole-day leave the fraction is exactly 1.00 per qualifying date, so the
accumulator reduces to the old count, and the rest-day/holiday skips were
already there. **No seeded demo number drifted.**

The one behavioural change is intentional and does not touch pay: a full-day
request spanning a weekend now deducts fewer *balance* days (Fri–Mon = 2, not 4).
Payroll already ignored the weekend, so pay is identical.

## Tests
- `leave/PartialLeaveIT` (10) — half day → 0.50; 2h of an 8h day → 0.25; 8h → 1.00;
  full day Fri→Mon → 2.00; approving a half day adds exactly 0.5 to the balance
  (asserted as a delta so it survives other tests touching Amir); HALF_DAY/HOURS
  with start ≠ end → 400; hours 0 / 9 / missing → 400; Sat–Sun range → 400; leave
  on a public holiday → 400 (in both units) while a range *spanning* it still
  works and charges only the working day; end before start → 400; unauthenticated
  → 401.
- `payroll/PartialLeavePayrollIT` (1, four employees) — unpaid half day →
  RM3,900.00; unpaid 2h → RM3,950.00; paid half day → RM4,000.00; unpaid Fri→Mon
  → RM3,600.00, with the payslip fractions (0.50 / 0.25 / 0 / 2.00) and labels.
- `payroll/CompensationServiceTest` — 4 new unit cases pinning the half-day,
  hourly, weekend-spanning and paid-partial math to the sen; the existing
  `unpaidDays` assertions moved to `isEqualByComparingTo` now the field is decimal.
- `leave/LeaveFlowIT` — asserts the FULL_DAY default for a body with no
  `durationUnit`.

Gate: `./scripts/test-backend.sh` → **BUILD SUCCESS**, 130 tests (37 unit + 93
integration), 0 failures. All 82 pre-existing integration tests stay green.

## Notes for next time
- Over-entitlement is still **not** blocked (out of scope, unchanged from before).
  A half day can take a balance negative exactly as a full day could.
- Accrual, carry-forward, join-date proration, and mixed partial days inside a
  multi-day range remain Phase 2.
- `payslips.paid_days` is still `INT`. If the UI ever surfaces it prominently,
  widen it to `NUMERIC(5,2)` in a follow-up migration rather than rounding.
