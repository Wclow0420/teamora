# 2026-09-28 — Employee hub-and-spoke admin screens (app)

Frontend half of the Employee Hub spec (PART B). Pairs with
`2026-09-28-safe-partial-employee-update.md`, which made `PATCH /api/employees/{id}`
genuinely partial — without that, every slice save here would wipe the employee's
reporting manager and work location.

## Why
`app/admin/employee-edit.tsx` had grown into one very long scroll: identity, role,
manager, work location, salary, tax profile, compensation, statutory & bank, leave
entitlement. Adding a person meant the same wall of fields before they even existed.

## What changed
**The hub** — `app/admin/employee-edit.tsx` (route name kept; the staff list already
links here). Identity card (avatar, name, read-only email, role chip) plus a
2-column tile grid of five categories. Each tile: tinted `IconTile`, the category
title, and a ONE-LINE recap of the current values — or a muted "Not set". Values come
from `useEmployee(id)` (the detail endpoint returns *effective* compensation,
statutory/bank and work location) inside an `AsyncBoundary`; the Leave tile reads
`useAdminLeaveBalances(id)`.

| Tile | Accent | Summary format |
| --- | --- | --- |
| Profile | coral | `jobTitle · department` |
| Employment | sage | `Role · manager name` (or `· Owner approves`) |
| Compensation | amber | `RM 4,000 · Mon–Fri · 8h` (`No salary` when unset) |
| Statutory & bank | violet | `NRIC set · Maybank · EPF no.` |
| Leave entitlement | neutral | `Annual 16 · Medical 14` (first two types) |

**Five sub-screens**, each registered in `app/admin/_layout.tsx`, each `Screen` +
`ScreenHeader back` with the employee's name as subtitle, each rendering only its own
fields and PATCHing only its own slice, then `router.back()`:
`employee-profile` (name, job title, department), `employee-employment`,
`employee-compensation` (salary + `CompensationFields`), `employee-statutory`
(`StatutoryBankFields` + tax profile), `employee-leave` (`LeaveEntitlementFields`).

**The clear flags live in exactly one place.** Only `employee-employment.tsx` sends
`reportingManagerId`/`workLocationId`, and when its picker is "None" it sends
`clearReportingManager: true` / `clearWorkLocation: true` instead of a null id. Every
other sub-screen omits all four keys, so those links survive a profile or salary edit.
The two flags were added to `UpdateEmployeeBody` in `src/api/types.ts` with a comment
spelling out the set / clear / leave-unchanged contract — that comment is the guard
rail for whoever adds the next sub-screen.

**Add employee is short now** — `app/admin/employee-new.tsx` is full name, work email,
temporary password, role, optional job title. On success it `router.replace`s into the
new employee's hub (using the id from the create response), where the remaining tiles
read "Not set".

Shared parsing/label helpers moved to `src/lib/employeeFields.ts` (`parseSalary`,
`parseHours`, `parseChildren`, `ringgit`, `summaryLine`) so five screens don't each
carry a copy.

## Non-obvious bits
- **Invalidation was already correct**: `useUpdateEmployee` invalidates `['employees']`,
  and React Query matches by key *prefix*, so `['employees', 'detail', id]` (the hub's
  query) is covered. Documented on the hook rather than changed — the same is true of
  `['leave']` covering the admin leave balances. Don't add `exact: true` there.
- **Join date is read-only** on the Employment screen: `UpdateEmployeeBody` has no
  `joinDate`, so the spec's "joinDate" field is shown as a sunken info row instead of
  an editable one. Making it editable is a backend change (and it prorates first-year
  leave, so it deserves its own thought).
- **The Leave screen has no employee PATCH at all** — `LeaveEntitlementFields` saves
  each type through the admin override endpoint with its own "Save entitlement" button,
  so the screen's own button is just "Done". Empty leave-type list → an honest
  `EmptyState` pointing at company settings.
- The staff list no longer passes the dozen-plus params it used to (the hub fetches
  detail); it passes `id`, `name`, `email`, `role` purely so the identity card and role
  chip render instantly while the detail loads.
- Typed routes: `.expo/types/router.d.ts` must be regenerated (boot `expo start` once)
  before `tsc` knows the five new `/admin/employee-*` pathnames.

## Gate
`npm run typecheck` — clean. No new dependency, no `app.config.ts` change, so this is
OTA-deliverable.
