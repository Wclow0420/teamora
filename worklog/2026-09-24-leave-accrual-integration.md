# 2026-09-24 — Leave accrual, carry-forward & proration: integration

Backend (V16) and frontend built by subagents. This entry covers integration.

## Verified (dev, end-to-end via API)
- Backend gate `./scripts/test-backend.sh` → BUILD SUCCESS; +23 new tests
  (LeaveYearTest ×6, LeaveAccrualIT ×17). All pre-existing tests green.
- V16 hot-swapped into the dev container. The old unique constraint
  `uq_leave_balance_emp_type (employee_id, leave_type_id)` — whose real name the
  agent confirmed against the live DB rather than guessing — was replaced by
  `uq_leave_balance_emp_type_year (employee_id, leave_type_id, leave_year)`.
  Existing balances backfilled to leave year 2026.
- **Regression (the one that mattered): seeded demo balances read identically** —
  ANNUAL 16/4 → 12, MEDICAL 14/6 → 8, EMERGENCY 5/2 → 3, all FIXED_ANNUAL so
  `accruedToDate == entitled`.
- Lazy provisioning works: an UNPAID balance row was created on read (accrual
  NONE, entitled 0) without a scheduler.
- `leaveYearStartMonth: 1` on company settings; `carryForwardMaxDays` on every
  leave type.
- Admin entitlement override: 18 → remaining 14; restored to 16 → 12.
  MANAGER → 403, unauthenticated → 401, negative entitled → 400.
- `npm run typecheck` clean. Dev data restored to its original state.

## Formulas (as implemented)
```
accruedToDate  FIXED_ANNUAL → entitled; NONE → 0
               MONTHLY_ACCRUAL → round2(entitled × elapsedMonths / 12)
available      accruedToDate + carriedForward − used     (not clamped)
entitled       join date inside this leave year
                 → round2(full × (12 − monthIndex(joinDate)) / 12)
carriedForward min(max(prev.entitled + prev.carriedForward − prev.used, 0), cap)
```
Leave-year identity is the calendar year it STARTS in, so with a start month of
April, 2027-01-15 belongs to leave year 2026.

## Notable decisions
- The backend agent added optional `joinDate` to employee create/update —
  proration was otherwise unreachable outside the demo seeder. Additive.
- The staff balance progress bar now divides by `entitled + carriedForward` (the
  real pool); otherwise carried-over days pushed the ratio negative.
- Entitlement saving in the admin employee form is its own action, deliberately
  not entangled with the main "Save changes" payload.

## Delivery status
OTA-deliverable. Native batch still pending owner go: expo-location (geofence),
expo-camera (selfie), expo-file-system (statutory export).
