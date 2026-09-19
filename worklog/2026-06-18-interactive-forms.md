# 2026-06-18 — Make the app interactive (write flows + management screens)

## What we built

Turned the app from read-mostly into a usable product by adding the write flows
the backend already supported. No new backend was needed.

### Form kit (`src/components/ui`)
- `TextField` (labelled input, icon, error, multiline), `SelectChips`
  (single-choice chip group, generic over a string union), `DateField`
  (native picker via `@react-native-community/datetimepicker`) + `toISODate`.

### API additions
- `endpoints.ts`: `authApi.register`, `employeeApi.create`/`changeRole`,
  `companyApi.me`/`update`. Added `api.patch` to the client (role/company use PATCH).
- `queries.ts`: `useCreateEmployee`, `useChangeRole`, `useCompany`,
  `useUpdateCompany` (with cache invalidation).
- `AuthContext.signUp(companyName, fullName, email, password)` → register +
  store tokens + route to admin (owner).
- `types.ts`: `RegisterBody`, `CompanyResponse`, `UpdateCompanyBody`,
  `CreateEmployeeBody`, `ChangeRoleBody`.

### New screens
- Staff: `leave-apply` (type + dates + reason → apply), `claim-submit`
  (category/title/amount/date → submit).
- Auth: `register` (company signup → owner → admin dashboard).
- Admin: `employee-new` (add staff + role), `employee-edit` (change an
  employee's role), `company-settings` (edit company profile, prefilled).

### Wired the triggers
- Staff Leave "Apply for leave" → `/leave-apply`; Claims "Add a claim" →
  `/claim-submit`.
- Login → "Create an account" → `/register`.
- Admin Staff "+" → `/admin/employee-new`; tapping a staff card → `/admin/employee-edit`.
- Admin Profile → "Company settings" → `/admin/company-settings`, "Team members"
  → the Staff tab.

## Verification
- `npx tsc --noEmit` clean; `npx expo export` bundles.
- Live API smoke (curl): submit claim → 200; company PATCH persists
  (phone/EPF no.); role PATCH (Employee → Manager) works. (register +
  add-employee already covered by the multi-tenant tests/smoke.)

## Follow-ups
- `employee-edit` only changes role (no full-profile update endpoint yet);
  Personal info / App settings / Privacy menu items are still placeholders.
- Receipt image upload for claims (the form captures fields, not a photo).
- Still mock: Calendar, Notifications, Scheduling.
