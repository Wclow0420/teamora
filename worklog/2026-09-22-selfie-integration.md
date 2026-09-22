# 2026-09-22 — Clock-in selfie: integration + verification

Backend (V13 photo storage + retrieval) and frontend (expo-camera live capture,
admin proof thumbnail, Profile fake-data cleanup) built by subagents. This entry
covers integration.

## Verified (dev, end-to-end via API)
- Backend gate `./scripts/test-backend.sh` → BUILD SUCCESS, 69 tests (incl. new
  ClockInPhotoIT, 8 cases). byte[]→bytea via `@JdbcTypeCode(SqlTypes.VARBINARY)`
  + `columnDefinition = "bytea"` (no `@Lob`), boots clean under `ddl-auto: validate`.
- V13 applied to dev DB via fast jar hot-swap; `clock_in_photo` (bytea) +
  `clock_in_photo_type` columns confirmed.
- Clock-in with a base64 JPEG → 200; admin Live board rows now carry
  `attendanceRecordId` + `hasPhoto` (true for the selfie clock-in, false otherwise).
- `GET /api/attendance/records/{id}/photo`: admin (same company) → 200 image/jpeg;
  other-company user → 403; unauthenticated → 401.
- Frontend `npm run typecheck` clean; `app.config.ts` resolves with BOTH native
  plugins (expo-location + expo-camera) and iOS/Android permissions.

## Notes
- Contract matched exactly across the boundary (LiveStaffRow fields, clock-in
  body, photo URL) — no reconciliation edits needed this round.
- Runtime test left Arjun clocked in today with a tiny (1×1) test selfie — a
  harmless demo artifact.
- Live-board query currently materialises photo bytes per row (small volume);
  a bytes-omitting projection is a possible future optimisation.

## Batch status (for the single build)
Native modules now queued for one preview build: **expo-location** (geofence) +
**expo-camera** (selfie). Delivery held for owner go/no-go.
