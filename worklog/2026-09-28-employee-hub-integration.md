# 2026-09-28 — Employee hub UX + safe partial update: integration

Backend (safe partial PATCH) and frontend (hub-and-spoke employee screens) built
by subagents. This entry covers integration and the two bugs caught along the way.

## Bug 1 (data loss) — found before building, fixed
`PATCH /api/employees/{id}` guarded every scalar with `if (req.x() != null)`, but
set the reporting manager and work location UNCONDITIONALLY. So `{"jobTitle":"x"}`
silently cleared both. Verified live against dev before any UI work: Arjun's
`reportingManagerId` went to null from a job-title-only PATCH.

Fix: explicit `clearReportingManager` / `clearWorkLocation` flags —
id → set, flag true → clear, neither → unchanged. Re-verified live after the
hot-swap: a job-title-only PATCH now preserves the manager in both the PATCH
response and a fresh GET, and the clear flag still works.

This bug already existed in the shipped build; hub-and-spoke just would have
triggered it on every section save.

## Bug 2 (phantom clear) — caught by the backend agent
Once a PATCH can leave `workLocation` untouched, it stays an uninitialized lazy
proxy, and `EmployeeResponse.build` reads a non-initialized proxy as "unassigned".
The PATCH response would have reported `workLocationId: null` for a row that still
had one, so the UI would paint it as cleared. `update()` now loads via the
fetch-joining query `findByIdAndCompanyIdWithManager`, same as `get()`.

## Bug 3 (my fix) — join date was made read-only on a wrong assumption
The frontend agent left join date read-only, reporting that "the backend PATCH
doesn't accept one". It does — `EmployeeService.update` has
`if (req.joinDate() != null) e.setJoinDate(...)`, proved live (2024-01-08 →
2024-03-12, HTTP 200). That mattered: **join date drives first-year leave
proration**, so leaving it uneditable made the Phase 2 proration work unreachable
from the UI. Added `joinDate` to `CreateEmployeeBody`/`UpdateEmployeeBody` and
replaced the read-only card in the Employment screen with a `DateField`
(max = today) that saves with the rest of the slice.

## Structure now
Tapping a staff member opens a HUB (identity header + 2-column tile grid with
one-line summaries, "Not set" when empty): Profile / Employment / Compensation /
Statutory & bank / Leave entitlement. Each tile opens a focused screen that saves
only its own slice. Add-staff shrank to essentials (name, email, temp password,
role, optional job title) and drops into the new hire's hub.

## Verified
- Backend gate BUILD SUCCESS, 121 tests (was 110; +11 EmployeePartialUpdateIT).
- Jar hot-swapped to dev; the regression re-verified live; Arjun's data restored
  (job title, manager, join date all back to their seeded values).
- Only `employee-employment.tsx` writes the manager/work-location/clear fields
  (grepped across app/ and src/) — so no other slice save can wipe them.
- `npm run typecheck` clean.

## Delivery
No native dep, no migration ⇒ shipped as an OTA update on runtime 1.1.0.
