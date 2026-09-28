# 2026-09-28 — Fix data-loss bug in PATCH /api/employees/{id} (safe partial update)

Backend-only. Prerequisite for PART B of the Employee Hub spec (hub-and-spoke
employee editor), where each sub-screen saves only its own slice of fields.

## The bug
`EmployeeService.update` guarded every scalar with `if (req.x() != null)`, but the
two association lines ran **unconditionally**:

```java
e.setReportingManager(resolveReportingManager(companyId, req.reportingManagerId(), e.getId()));
e.setWorkLocation(resolveWorkLocation(companyId, req.workLocationId()));
```

Both resolvers return `null` for a null id, so `PATCH {"jobTitle":"x"}` silently
wiped `reportingManagerId` **and** `workLocationId` (verified live against the dev
API). With hub-and-spoke that destroys data on every section save — and clearing a
reporting manager quietly re-routes that employee's leave/claim approvals to the
company OWNER (the null-manager fallback), so it is worse than a cosmetic loss.

## The fix — explicit clear flags
`UpdateEmployeeRequest` gains `Boolean clearReportingManager` and
`Boolean clearWorkLocation`. Per association, precedence is:

1. id non-null → resolve (full existing validation) and set it
2. else clear flag `TRUE` → set null
3. else → **leave unchanged**

`Boolean.TRUE.equals(...)` is used, so `false` and absent behave identically
(leave unchanged). An id present alongside a true clear flag → the id wins.
`resolveReportingManager` / `resolveWorkLocation` are untouched: same company,
not self, manager must be OWNER/HR_ADMIN/MANAGER, work location must belong to
the company. **No other field's semantics changed, and the create path is
unchanged** (there, a null id still legitimately means "none").

### Secondary fix found while doing this (response fidelity)
`update()` loaded the employee with `findByIdAndCompanyId` (no fetch join). Once a
PATCH can leave the associations untouched, the untouched `workLocation` stays an
uninitialized lazy proxy, and `EmployeeResponse.build` deliberately reads a
non-initialized proxy as "unassigned" — so the PATCH response would have reported
`workLocationId: null` for a row that still had one, and the app would paint the
value as cleared. `update()` now loads via `findByIdAndCompanyIdWithManager`
(fetch-joins company + reportingManager + workLocation), the same query `get()`
already uses. Behaviour is otherwise identical (still 404 when not in the company).

## Files changed
- `backend/src/main/java/com/teamora/employee/dto/EmployeeDtos.java` — two new
  `Boolean` fields on `UpdateEmployeeRequest` (+ javadoc on all four).
- `backend/src/main/java/com/teamora/employee/EmployeeService.java` — tri-state
  association handling; fetch-join in `update()`; corrected the
  `resolveWorkLocation` comment (a null id no longer "clears").
- `backend/src/main/java/com/teamora/employee/EmployeeController.java` — javadoc
  spelling out that omission never clears.
- `backend/src/test/java/com/teamora/employee/EmployeePartialUpdateIT.java` — new,
  11 tests.

## Tests
`EmployeePartialUpdateIT` registers a throwaway company per test (nothing leaks
into the shared test DB). It covers: the regression (PATCH only `jobTitle` keeps
both associations — asserted on the response *and* on a fresh GET); three
consecutive unrelated section saves keeping both; set → `null` id is a no-op →
explicit clear nulls it, for each association; `clear*: false` leaves them alone;
id beats clear flag; and the validations still firing (self-as-manager 400,
cross-company manager 400, cross-company work location 400, plain EMPLOYEE as
manager 400, `role: OWNER` via PATCH still 400 even with a clear flag alongside).

**No existing test needed changing.** Searched the test sources for
`reportingManagerId` / `workLocationId`: every hit either sets an id explicitly
(`EmployeeManagementIT`, `GeofenceClockInIT`, `ApprovalRoutingIT`,
`OvertimeFlowIT`) or is on the create path — nothing relied on "omitting the id
clears it".

## Gate
`cd backend && ./scripts/test-backend.sh` → **BUILD SUCCESS**, 121 tests
(0 failures), up from 110. The running dev stack was deliberately **not** rebuilt
or restarted — the owner hot-swaps the jar.
