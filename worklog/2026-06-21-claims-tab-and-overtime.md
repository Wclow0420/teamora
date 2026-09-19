# 2026-06-21 — Admin Claims tab + Overtime (OT) domain + a security fix

## 1. Shared approval cards + admin Claims tab
- Extracted `src/components/approvals/ApprovalCards.tsx` (`LeaveApprovalCard`,
  `ClaimApprovalCard`, `OvertimeApprovalCard`, `DecideRow`, `ApprovalTabs`,
  `EmptyApprovals`) used by both the manager inbox and admin Approvals.
- Wired the admin Approvals **Claims** tab (Owner/HR see all claims) — it was a
  static placeholder before. Both screens are now tab-driven and reuse the cards.

## 2. Overtime (OT) — new domain (hours + reason, approval-only)
- **Backend:** migration `V4` (`overtime_requests`); `overtime` package
  (entity/repo/service/controller/DTOs/seeder), routed exactly like leave/claims
  (manager → reports; HR/Owner override). Endpoints: `GET/POST /api/overtime`,
  `GET /api/admin/overtime`, approve/reject. Demo OT seeded for Amir.
  `OvertimeFlowIT` covers submit + routing + RBAC.
- **App:** types/endpoints/hooks (`useOvertime`, `useSubmitOvertime`,
  `usePendingOvertime`, `useDecideOvertime`); an **OT tab** added to both the
  manager inbox and admin Approvals; a **Log overtime** form
  (`app/(staff)/overtime-submit.tsx`, date + hours + reason, with the
  employee's recent OT) reached from a button on the Attendance screen.
  `DateField` gained `maximumDate`.

## 3. Security fix — 401 vs 403 (latent bug)
OT testing surfaced it: an authenticated-but-forbidden request to `/api/admin/**`
was returning **401** instead of **403** live (MockMvc tests still saw 403).
Root cause (found via Spring Security DEBUG logs): the access-denied handler's
`sendError(403)` triggers a servlet **ERROR dispatch to `/error`**, which got
re-secured as anonymous → entry point → 401, overwriting the 403. **Fix:** add
`/error` to the permitAll matchers, plus an explicit `accessDeniedHandler` (403).
Now: unauthenticated → 401, authenticated-but-forbidden → 403 (verified live).

## Verification
- Backend: `./scripts/test-backend.sh` → **25 tests, BUILD SUCCESS**. Live:
  401/403 split correct; Nadia (MANAGER) sees only her reports' OT; Sarah (HR)
  sees all; OT submit works.
- App: `tsc` clean + iOS bundle builds.

## Notes
- `docker-compose.yml` gained a `SEC_LOG` env toggle (defaults INFO) for
  `org.springframework.security` debug logging.
