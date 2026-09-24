# 2026-09-24 — Partial-day leave: integration + verification

Backend (V15 fractional leave + payroll coupling) and frontend (duration picker,
fractional balances) built by subagents. This entry covers integration.

## Verified (dev, end-to-end via API)
- Backend gate `./scripts/test-backend.sh` → BUILD SUCCESS, **130 tests**
  (37 unit + 93 integration), up from 82. Includes PartialLeaveIT and
  PartialLeavePayrollIT proving the unpaid fractions to the sen.
- V15 hot-swapped into the dev container; column conversions confirmed:
  `leave_requests.days`, `leave_balances.entitled/used`, `payslips.unpaid_days`
  all `numeric`; new `duration_unit` / `half_day_period` / `hours` / `start_time`.
  (The backend agent also dry-ran the migration against the real dev DB inside a
  rolled-back transaction before we applied it.)
- Runtime checks, all as designed:
  - HALF_DAY (AM) → `days 0.50`, label "Half day (AM)".
  - HOURS 2h of an 8h day → `days 0.25`, label "2 hours"; `startTime "14:00"`
    (HH:mm, no seconds) parsed fine — the format the app sends.
  - FULL_DAY Fri→Mon → `days 2.00`, i.e. the weekend is excluded (the correctness
    fix; previously this consumed 4 calendar days of balance).
  - Validation: half-day spanning two dates → 400; 12 hours on an 8h day → 400
    with "That's more than a working day (8 hours) — apply for a full day instead".
- Existing payroll figures unchanged (PayrollRunIT / CompLeavePayrollIT /
  PayrollExportIT all still pinned) — whole-day leave still yields exactly 1.00
  per qualifying date.
- Test requests deleted afterwards; amir's balances untouched (16.00/4.00 etc.).
  `npm run typecheck` clean.

## Design notes
- `LeaveDurationCalculator.hourFraction(...)` is shared by the balance deduction
  and `CompensationService`, so the days charged to a balance and the days
  deducted from pay are computed with identical arithmetic and cannot drift.
- `CompensationService` now accumulates `Map<LocalDate, BigDecimal>` fractions
  (capped at 1.00 per date) instead of whole-day `Set<LocalDate>` counts; money
  still rounds once at 2dp HALF_UP after the multiply.

## Known follow-up
- `payslips.paid_days` is still `INT`, so the informational paid-days figure is
  rounded HALF_UP while the money uses the exact fraction. Widening it to
  NUMERIC is a small future migration.

## Delivery status
OTA-deliverable (no native dep). Native batch still pending owner go:
expo-location (geofence) + expo-camera (selfie) + expo-file-system (export).
