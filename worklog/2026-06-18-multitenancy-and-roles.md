# 2026-06-18 — Multi-tenancy + role hierarchy (employer/company)

## What changed

Turned the backend into a **multi-tenant HR SaaS**: companies (employers) are
the tenant, data is isolated by `company_id`, and roles became a proper
hierarchy. The app keeps working (no new screens), with minimal routing tweaks.

### Schema (Flyway `V2__multitenancy_and_roles.sql`)
- New `companies` table (name, slug, registration/EPF/SOCSO nos, timezone,
  currency, …).
- `company_id` (NOT NULL, FK, indexed) added to `employees`, `attendance_records`,
  `leave_balances`, `leave_requests`, `claims`, `payslips`. Additive + backfill
  so it's safe on a populated DB.
- Role values migrated: `ADMIN`→`HR_ADMIN`, `STAFF`→`EMPLOYEE`.

### Backend
- `company` package: `Company` entity, repo, service (slug generation),
  controller (`GET/PATCH /api/companies/me`).
- `common.TenantEntity` (extends BaseEntity, adds non-null `company`); all
  tenant entities now extend it (`Employee` + the 4 domains).
- `Role` enum → `OWNER, HR_ADMIN, MANAGER, EMPLOYEE` with `isManagement()` /
  `canManageEmployees()`. Security: `/api/admin/**` → management roles;
  employee management → OWNER/HR_ADMIN (method security).
- **Tenant scoping:** services set `company` on every created row; admin/list
  queries filter by the caller's company id; approve/reject verify the row's
  company matches the admin's (no cross-tenant leak); employee directory is
  company-scoped.
- **Onboarding:** `POST /api/auth/register` (company + OWNER, public);
  `POST /api/employees` (add employee); `PATCH /api/employees/{id}/role`.
- JWT now carries a `cid` (company) claim. Seeder creates **two** demo companies
  (Lumi Foods + Nusantara Tech).

### App
- `api/types.ts`: `Role` union updated; `EmployeeResponse` gains
  `companyId`/`companyName`; `isManagementRole()` helper. Login/entry route
  EMPLOYEE → staff app, management roles → admin app.
- Fixed an app↔API field mismatch surfaced during testing: live board returns
  `staff` (app type/`live.tsx` had `rows`).

### Two bugs found & fixed during verification
- `LazyInitializationException` on `/me` + `/companies/me` (company is LAZY,
  `open-in-view: false`) → `CurrentEmployeeService` now fetches the company
  eagerly (`findByEmailWithCompany`).
- `lower(bytea)` Postgres error in employee search (null String params typed as
  bytea) → switched to empty-string sentinels.

## Verification (docker build + compose up, fresh DB)
- V1+V2 migrate, JPA **validation passes**, seeds 2 companies / 12 employees.
- Smoke test: roles + company on login; `/me` + `/companies/me` OK; **tenant
  isolation** (Lumi admin sees 10 employees, Nusantara sees 2; live board +
  pending leave scoped); RBAC (EMPLOYEE → admin endpoint = 403); **signup**
  (company + OWNER) + **add employee** + new employee can log in; Acme can't see
  Lumi's people. App `tsc` clean + iOS bundle builds.

## Follow-ups
- Custom per-company roles/permissions (schema is ready for it).
- Mobile screens for company signup + employee management (deferred this round).
- Scheduling/calendar/notifications endpoints still pending (app uses mock).
