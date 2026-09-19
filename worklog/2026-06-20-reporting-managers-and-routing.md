# 2026-06-20 — Single owner, reporting managers & routed approvals

Implements the approved plan (`.claude/plans/owner-role-only-can-fancy-gray.md`).

## Backend
- **Migration `V3`**: `employees.reporting_manager_id` (self-FK, nullable) +
  index; partial unique index `uq_employees_one_owner_per_company` (one OWNER/company).
- **Single owner / transfer:** OWNER rejected in create/update/changeRole;
  `POST /api/employees/{id}/transfer-ownership` (owner-only) demotes the current
  owner → HR_ADMIN, **flushes**, then promotes the target → OWNER (so the partial
  index never transiently sees two owners).
- **Reporting manager:** `Employee.reportingManager`; `EmployeeResponse` +=
  `reportingManagerId/Name` (lazy-guarded `from()` for `/me`; fetch-join
  `withManager()` for list/get). Create/update accept `reportingManagerId`
  (validated: same company, role ∈ OWNER/HR_ADMIN/MANAGER, not self →400).
  `GET /api/employees/managers` lists assignable managers.
- **Approval routing:** `LeaveService`/`ClaimService` `pending(caller)` —
  HR_ADMIN/OWNER see all company pending; MANAGER sees only their reports'.
  `approve`/`reject` add `assertCanDecide` → HR/Owner override, else must be the
  requester's reporting manager, else `AccessDeniedException` (403). New repo
  queries `findPendingForReportingManager` + `findByIdWithEmployeeAndManager`.
- **Seeder:** demo reporting structure (Retail/Marketing → Nadia (MANAGER), rest
  → Sarah (HR_ADMIN); managers → owner).
- **Tests:** `OwnershipTransferIT` (transfer swaps roles, non-owner 403, no 2nd
  owner via create/update/changeRole), `ApprovalRoutingIT` (manager sees/decides
  only reports, owner override + default approver), extended `EmployeeManagementIT`
  (reporting manager set/validated, managers endpoint). **23 IT green.**

## App
- Routing: new `isAdminRole` (OWNER/HR_ADMIN → admin app); MANAGER + EMPLOYEE →
  staff app (`app/index.tsx`, login).
- `app/(staff)/approvals.tsx` — manager Approvals inbox (Leave + Claims tabs,
  approve/decline), reached from a manager-only card on staff Home.
- API: `EmployeeResponse`/bodies += `reportingManagerId`; `ManagerOption`;
  `employeeApi.managers`/`transferOwnership`; hooks `useManagers`,
  `useTransferOwnership`.
- `ReportingManagerField` (new) used in add/edit employee. Edit drops OWNER from
  the role picker (shows a read-only "Owner" chip for the owner) and adds a
  **Transfer ownership** button (owner-only) with an `Alert` confirm.

## Verification
- Backend: `./scripts/test-backend.sh` → 23 tests, BUILD SUCCESS. Rebuilt dev
  API on a fresh DB (V3 applied, demo structure seeded). Live: Nadia (MANAGER)
  pending = 1 (Amir, her report); Sarah (HR_ADMIN) = 3 (all); managers list
  excludes employees; Amir → reportingManager Nadia.
- App: `tsc` clean + iOS bundle builds.

## Notes
- Managers calling `/api/admin/**` from the staff app is intentional (MANAGER
  role permits it; results are role-aware).
- The admin Approvals tab's Claims/OT sub-tabs remain static (HR claims-approval
  UI is a separate existing gap); the manager inbox handles both leave + claims.
