# Leave accrual, carry-forward & join-date proration — backend

Backend half of the Phase-2 leave engine. Balances stop being one flat row per
(employee, leave type) forever and gain a **leave year** dimension, so entitlement
resets each year, unused days can roll over, and someone who joins in August no
longer gets a full year's leave. All `backend/**`; the app half shipped separately
(`2026-09-24-leave-accrual-app.md`) and the JSON contract lines up field for field.

## Migration — `V16__leave_accrual_carry_forward.sql`

- `company_settings.leave_year_start_month INT NOT NULL DEFAULT 1` + a named CHECK
  (`ck_company_settings_leave_year_start_month`, 1–12).
- `leave_types.carry_forward_max_days NUMERIC(5,2) NOT NULL DEFAULT 0`.
- `leave_balances.leave_year INT` (backfilled to `EXTRACT(YEAR FROM CURRENT_DATE)`
  then `SET NOT NULL` — every company is on start month 1 at migration time, so the
  current leave year is just the calendar year) + `carried_forward NUMERIC(6,2) NOT NULL DEFAULT 0`.
- **Dropped `uq_leave_balance_emp_type`** — the real name, verified against the dev DB
  with `\d leave_balances` before writing the migration (V11 added it as a table
  constraint, not a bare index) — and replaced it with
  `CREATE UNIQUE INDEX uq_leave_balance_emp_type_year (employee_id, leave_type_id, leave_year)`.

Dry-run against the live dev DB inside a rolled-back transaction confirmed the
backfill (3 seeded rows → 2026) and that Amir still reads 16/4, 14/6, 5/2 for the
current leave year. The dev stack was **not** rebuilt or restarted.

## Model

- **`LeaveYear`** (new, `leave/`) — pure helper. A leave year is named after the
  calendar year it *starts* in, which is what makes an April–March year unambiguous:
  `yearOf(2027-01-15, startMonth=4) == 2026`. Also `startOf` / `endOf` /
  `monthIndexWithinYear` (0–11) / `contains` / `normaliseStartMonth`.
- **`LeaveBalance`** — `leaveYear`, `carriedForward`, and the *derived* figures:
  `accruedToDate(startMonth, today)` and `available(...)`. `remaining()` is gone;
  "what's left" is now `available` = `accruedToDate + carriedForward − used`.
  `availableAtYearEnd()` is the carry-forward source (a closed year is fully accrued).
- **`LeaveType.carryForwardMaxDays`**, **`CompanySettings.leaveYearStartMonth`**.

### The formulas, as implemented

```
accruedToDate  FIXED_ANNUAL    → entitled
               MONTHLY_ACCRUAL → round2(entitled × elapsedMonths / 12)
               NONE            → 0
elapsedMonths  past leave year → 12; future → 0;
               current         → clamp(monthIndexWithinYear(today) + 1, 0, 12)
available      accruedToDate + carriedForward − used      (may be negative)
entitled       joinDate inside this leave year
                 → round2(full × (12 − monthIndexWithinYear(joinDate)) / 12)
               joinDate after the year ends → 0; before, or null → full
carriedForward min(max(prev.entitled + prev.carriedForward − prev.used, 0), cap)
               where prev is the (employee, type, year−1) row, if one exists
```

Everything is `BigDecimal` at 2dp, `HALF_UP`. `available` is deliberately **not**
clamped: if an admin lowers an entitlement after leave was taken, the employee (and
their approver) should see `-1 day left`, not a comfortable zero.

## Services

- **`LeaveBalanceService`** (new) — owns all of the above.
  - `ensureBalances(employee, leaveYear)` — **lazy provisioning, no scheduler**:
    creates whatever is missing for the company's ACTIVE leave types whenever
    balances are read or leave is charged. Idempotent, so nothing has to "open" a
    new leave year on 1 January.
  - `balancesFor` / `balancesForEmployee` / `availableOn` / `override`.
- **`LeaveService`** — `myBalances(employee, year)` delegates to it; **`approve`
  now charges the leave year the request's `startDate` falls in**, not "the current
  year" (leave booked in December for January comes out of next year's pot), and
  creates that row via `ensureBalance` if it doesn't exist yet. The Approvals
  inbox's "N days left" is likewise the balance for *that request's* year.
- **`LeaveTypeService`** / **`CompanySettingsService`** — carry the two new fields
  through create/update/seed.
- **`LeaveSeeder`** — stamps demo balances with the current leave year.

## API

`GET /api/leave/balances?year=` — optional leave year, defaults to the current one.
`LeaveBalanceResponse` keeps every existing field name and JSON type (`entitled`,
`used`, `remaining` stay numbers; `remaining` is still the available figure the app
renders big) and adds `leaveYear`, `accruedToDate`, `carriedForward`, `accrual`:

```json
{ "leaveTypeId": "…", "code": "ANNUAL", "name": "Annual Leave", "colorKey": "coral",
  "paid": true, "accrual": "FIXED_ANNUAL", "leaveYear": 2026,
  "used": 4.00, "entitled": 16.00, "accruedToDate": 16.00, "carriedForward": 0.00,
  "remaining": 12.00, "usedLabel": "4 / 16", "remainingLabel": "12 days left" }
```

- `GET /api/admin/leave/balances?employeeId=&year=` — one employee's balances.
  Company-scoped (another tenant's id → 404); open to OWNER / HR_ADMIN / MANAGER
  because approvers need to see what someone has left.
- `PATCH /api/admin/leave/balances` — `{ employeeId, leaveTypeId, leaveYear?, entitled }`
  → the updated `LeaveBalanceResponse`. **OWNER / HR_ADMIN only** (`@PreAuthorize`);
  MANAGER and EMPLOYEE get 403, unauthenticated 401, negative `entitled` 400.
- `leaveYearStartMonth` on `GET`/`PATCH /api/admin/company-settings` (1–12, else 400).
- `carryForwardMaxDays` on leave-type create/update + the response.
- **`joinDate` added to the employee create/update payloads.** Not in the spec, but
  proration is dead code without it — nothing could set a join date through the API,
  only the demo seeder. Optional and additive, so no client breaks.

## Tests

`LeaveYearTest` (6 unit cases: start months 1 and 4, the Dec→Jan boundary, the
`monthIndex` wrap) and `LeaveAccrualIT` (17 cases: the three accrual modes with an
exact monthly fraction, join-date proration, capped and uncapped carry-forward, the
admin override incl. a deliberately negative balance, RBAC 401/403, tenant isolation
404, validation 400s, idempotent provisioning, and leave dated in the *next* leave
year charging that year's row).

Every IT fixture is its own freshly-registered company, and none of them hard-code
today's date: where a test needs a known point inside the leave year it moves the
company's `leaveYearStartMonth` instead, so the elapsed-month count is exact whatever
day the suite runs on.

**One existing test changed.** `PartialLeaveIT.approvingAHalfDay_addsHalfADayToTheBalance`
applies leave dated **2027**-04-05 and then asserted the delta on the *current* year's
balance. That is exactly the bug this phase fixes, so the assertion now reads
`?year=2027`. The test's intent (a 0.5-day delta, isolated on 2027 dates) is unchanged.

Gate: `./scripts/test-backend.sh` → **BUILD SUCCESS, 110 tests, 0 failures**.
`npm run typecheck` on the app is clean against the new shapes.

## Notes for the next person

- No figures moved. The seeded demo balances are all `FIXED_ANNUAL`, so
  `accruedToDate == entitled` and `remaining` is the same number it always was;
  payroll never reads `leave_balances` (it prices approved *requests*), so the
  pinned payslip amounts are untouched.
- Carry-forward only fires when a row for `year − 1` actually exists. Lazy
  provisioning means that's true for anyone who read their balances last year, and
  an admin can force it by reading `?year=<last year>` — no backfill job needed.
- `pending()` resolves one balance per row (N+1). Fine at demo scale; if the
  Approvals inbox ever gets slow, batch it by (employee, type, year).
- Not in this phase (deliberately): carry-forward expiry windows, anniversary-based
  leave years, tenure tiers, encashment, pro-rating on termination.
