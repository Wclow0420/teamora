# 2026-09-22 — Geofenced clock-in: integration + verification

Backend (V12, work_locations, geofence enforcement) and frontend (expo-location,
work-locations manager, per-staff assignment, GPS clock-in) built by subagents.
This entry covers integration.

## Integration fix I made
The staff clock-in screen gates on `me.data.workLocationId`, but `GET /api/employees/me`
returned it as null (the caller was loaded via `findByEmailWithCompany`, so
`workLocation` was a lazy proxy → dropped by the DTO's `isInitialized` guard).
Result would have been: assigned staff never send coords → backend rejects with
400 "Location required" → **assigned staff could never clock in.**

Fix (backend, one method): `EmployeeController.me()` now delegates to
`employeeService.get(companyId, callerId)`, the same fully-fetched detail builder
used by admin `GET /{id}` (fetch-joins `workLocation` + reporting manager, adds
effective comp + derived rates). `/me` now returns `workLocationId`/`workLocationName`,
so the existing frontend logic is correct and only assigned staff are prompted
for location. No frontend change needed.

## Verified (dev, end-to-end via API)
- Test gate `./scripts/test-backend.sh` → BUILD SUCCESS, 61 ITs (incl. new
  WorkLocationConfigIT + GeofenceClockInIT) + GeoUtilTest, ~21s (cached m2).
- V12 applied to dev DB via the fast jar hot-swap (docker cp + restart).
- Create work location (HR) ✓; employee create → 403 ✓; radius<50 → 400 ✓.
- Assign employee ✓; `/me` returns the assignment ✓ (the fix).
- Clock-in far (~9.75 km) → 400 "You're ~9751 m from Lumi HQ. Move within 100 m
  to clock in." ✓; clock-in at the pin → 200 ✓.
- Frontend `npm run typecheck` clean; `app.config.ts` resolves with the
  expo-location plugin + iOS/Android location permissions.
- Cleanup: test employee unassigned, test site deactivated → dev DB pristine.

## Delivery
- `expo-location` is native ⇒ needs a NEW preview build (NOT an OTA update).
  Held pending owner go/no-go per their instruction.

## Note
- The inside-range verification clocked Amir in for today (real attendance) — a
  harmless side effect of the happy-path test.
- `backend/Dockerfile.fast` remains the fast local rebuild path (the normal
  `docker compose up --build` takes ~27 min on this machine).
