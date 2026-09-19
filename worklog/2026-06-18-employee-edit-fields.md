# 2026-06-18 — Editable employee profile (name/title/dept) + show email

## What

The admin "Edit employee" screen now edits **name, job title, department, and
role**, and shows the employee's **email** (read-only — it's the login identity).
Previously it only changed the role.

## Backend
- New endpoint `PATCH /api/employees/{id}` (OWNER/HR_ADMIN), company-scoped.
  `UpdateEmployeeRequest { fullName?, jobTitle?, department?, phone?, staffId?,
  role? }` — partial update. Guards: can't change your own role; can't touch
  another tenant's employee (404). Kept the existing `/{id}/role` endpoint.
- Test `EmployeeManagementIT` (4): update name/title/dept/role; self-role-change
  rejected (400); cross-tenant update → 404; EMPLOYEE → 403. It creates
  throwaway employees rather than mutating seeded accounts.

## Frontend
- `types.ts UpdateEmployeeBody`; `employeeApi.update`; `useUpdateEmployee` hook.
- Staff list passes `email` in the params to the edit screen.
- `employee-edit.tsx` rewritten: identity card shows name + email (read-only),
  then editable Full name / Job title / Department + Role chips; one "Save
  changes" → `PATCH /api/employees/{id}`.

## Fixed along the way
Test-isolation bug: the first version of `EmployeeManagementIT` promoted the
seeded Amir to MANAGER, which leaked into `LeaveFlowIT` (shared test DB) and
flipped its "employee can't approve" expectation. Now the test edits its own
throwaway employees.

## Verification
- Backend: `./scripts/test-backend.sh` → **13 tests, BUILD SUCCESS**.
- App: `tsc` clean + iOS bundle builds.
- Live: `PATCH /api/employees/{id}` updates name/title/dept/role and returns
  email.
