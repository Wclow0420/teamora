# Leave accrual, carry-forward & proration — app (frontend)

Frontend half of the Phase-2 leave engine (spec: leave accrual / carry-forward /
proration). Backend lands separately; this is all `app/` + `src/`, no native
dependency and no `app.config.ts` change, so it ships OTA.

## API layer

- **`src/api/types.ts`**
  - `LeaveBalance` gains `leaveYear`, `accruedToDate`, `carriedForward`.
    `entitled` / `used` / `remaining` keep their meaning — `remaining` is still
    the "available now" figure the app shows big.
  - `CompanySettings` + `UpdateCompanySettingsBody` gain `leaveYearStartMonth` (1–12).
  - `LeaveTypeDef` + create/update bodies gain `carryForwardMaxDays`.
  - New `OverrideLeaveEntitlementBody` (`employeeId`, `leaveTypeId`, `leaveYear?`, `entitled`).
  - No separate `AdminLeaveBalance` type: the admin list returns the same
    `LeaveBalanceResponse` shape, so reusing `LeaveBalance` keeps one source of truth.
- **`src/api/endpoints.ts`** — `leaveApi.balances(year?)` now takes an optional
  leave year; new `leaveBalanceAdminApi` with `list(employeeId, year?)`
  (`GET /api/admin/leave/balances`) and `override(body)` (`PATCH /api/admin/leave/balances`).
- **`src/api/queries.ts`** — `useLeaveBalances(year?)` (key now carries the year),
  `useAdminLeaveBalances(employeeId, year?)` (disabled without an id) and
  `useOverrideLeaveEntitlement()` (invalidates `['leave']`).

## Screens

- **`app/(staff)/leave.tsx`** — the balance tile explains itself. `remaining`
  stays the headline; a small sub-line appears **only when it says something**:
  "N accrued so far" when `accruedToDate < entitled` (i.e. a monthly-accrual
  type — inferred, we don't invent a field the API doesn't send) and
  "N carried over" when `carriedForward > 0`, joined with "·". The progress
  ratio now divides by the real pool (`entitled + carriedForward`) so carried
  days don't push the bar out of range.
- **`app/admin/leave-type-edit.tsx`** — new **Carry-forward cap (days)** field
  (decimal-pad, >= 0, blank = 0) with "0 = unused days are forfeited at year end",
  wired into both the create and update payloads. Company settings passes the
  current value through as a route param when opening an existing type.
- **`app/admin/company-settings.tsx`** — **Leave year starts** month chips (Jan–Dec
  → 1–12) in the defaults form, helper "January = calendar year…". Section
  renamed "Payroll & leave defaults" since it is no longer payroll-only.
- **`src/components/LeaveEntitlementFields.tsx`** (new) + wired into
  **`app/admin/employee-edit.tsx`** — compact "Leave entitlement" section: one
  editable days field per leave type for the current leave year, with used /
  carried-over context per row, saved by its **own** "Save entitlement" button
  (only the changed rows are PATCHed) so it never entangles the main
  "Save changes" payload. Renders **nothing** when the balances are loading,
  errored or empty, so an older backend or a permissions error can't break the
  edit form.

## Notes for the next person

- Entitlement edits are held as a sparse "edits" map rather than seeded state:
  the displayed value falls back to the server number, so a refetch after saving
  always wins and there's no stale-seed bug.
- The staff sub-line reads `accruedToDate`/`carriedForward` defensively (missing
  → 0), so the screen still renders sensibly against a pre-V16 backend.
- Gate: `npm run typecheck` clean.
