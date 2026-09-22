# 2026-09-19 — Configurable comp + leave: integration, dev deploy, EAS update

Backend + frontend for the configurable compensation + leave engine were built
(see companion worklog `2026-09-19-configurable-comp-and-leave-backend.md`).
This entry covers integration, verification, and shipping.

## Contract reconciliation (frontend ↔ backend)
The backend renamed leave fields (`code`/`name`/`typeCode`) while the app still
declared `type`/`label`. Typecheck can't catch a JSON field mismatch across the
HTTP boundary, so I aligned the app to the backend's actual payload:
- `LeaveBalance`: `type`→`code`, `label`→`name` (used in `app/(staff)/leave.tsx`
  and `home.tsx` — would have rendered blank labels otherwise).
- `LeaveRequest`/`PendingLeave`: `type`→`typeCode` (only `typeLabel` is consumed).
- `src/api/types.ts` + the two consumer screens updated. `npm run typecheck` clean.

## Dev deploy (V11 applied to local DB)
The normal `docker compose up --build api` took ~27 min on this machine (the
Dockerfile's Maven stage can't mount the cached `teamora-m2` volume, so it
re-resolves all deps every build). Instead:
- Used the **jar already built by the test run** (`target/teamora-api-1.0.0.jar`,
  contains V11 + all new classes), hot-swapped it into the running container
  (`docker cp` → `docker restart`). Seconds, not minutes.
- Added **`backend/Dockerfile.fast`** — a runtime-only image that copies a
  host-built jar into a slim JRE, for fast local rebuilds without the 27-min
  Maven-in-Docker step. (Follow-up idea: mount `teamora-m2` in the real build or
  split the compose build so normal rebuilds are fast too.)
- V11 applied cleanly; `leave_types` seeded per company (ANNUAL/MEDICAL/EMERGENCY
  paid + UNPAID unpaid). Note: during the killed builds the db+api containers
  went down once and were restarted — no data loss (named volume).

## Verified end-to-end (localhost + through ngrok)
- `GET /api/leave/types`, `/api/leave/balances` (new shape: leaveTypeId/code/name/colorKey/paid).
- `GET /api/admin/company-settings` → `{MONTHLY, 31, 8.00}`; employee 403 (RBAC).
- Employee detail derived rates: Amir RM4,000 ÷ 22 Sep-2026 working days = **181.82/day**, ÷8 = **22.73/hr**.
- Same endpoints confirmed reachable through the ngrok tunnel (the phone's path).

## Shipped
- `eas update --channel preview` published: branch `preview`, runtime `1.0.0`,
  iOS+Android. Update group `de0f3832-90ac-4903-904a-eae112a269e2`. Reaches the
  installed preview build on next launch.

## Follow-ups (v2 / noted, not done)
- Carry-forward, join-date proration, tenure accrual, bonus source, writing
  ON_LEAVE attendance on leave approval, multi-country statutory.
- Profile screen still shows placeholder "12 available"/"4 files" (pre-existing,
  violates the no-fake-data rule) — separate cleanup.
- Speed up the real Docker build (m2 cache) so `Dockerfile.fast` isn't needed.
