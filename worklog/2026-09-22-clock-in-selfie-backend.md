# Clock-in selfie storage (backend)

**Date:** 2026-09-22
**Scope:** backend only (`backend/**`) — app/RN wiring lands with the batched
preview build (native `expo-camera`), tracked separately.

## What & why
Store a real front-camera selfie captured at clock-in as server-side attendance
proof (pairs with the existing geofence). Photo is stored inline in Postgres
(`bytea`) on the attendance row — durable via the existing DB volume, no
filesystem/object-store infra. The photo is **optional and non-blocking**: if the
client can't capture one (permission denied/unavailable), clock-in proceeds
exactly as before. Geofence behaviour is unchanged.

## Changes
- **Migration `V13__clock_in_photo.sql`** — adds nullable `clock_in_photo BYTEA`
  + `clock_in_photo_type VARCHAR(32)` to `attendance_records`. No seeding.
  (Highest prior migration was V12 — verified.)
- **`AttendanceRecord`** — added `byte[] clockInPhoto` + `String clockInPhotoType`.
  Mapped `byte[]` → Postgres `bytea` the Hibernate-6 way: plain `byte[]` with
  `@JdbcTypeCode(SqlTypes.VARBINARY)` + `@Column(columnDefinition = "bytea")`.
  Deliberately **no `@Lob`** (that maps to a large-object OID and breaks
  `ddl-auto: validate`). Marked `@Basic(fetch = LAZY)` (effective only with
  bytecode enhancement); the live board derives `hasPhoto` from the small
  `clockInPhotoType` column so it never depends on the bytes being loaded.
- **`ClockInRequest` DTO** — added optional `String photoBase64`. Accepts a bare
  base64 string **or** a `data:image/...;base64,...` data URL (prefix stripped,
  content-type inferred; whitespace tolerated).
- **`AttendanceService.clockIn`** — new 4-arg overload (kept the old 3-arg for
  callers). After the geofence check, if a photo is present it's decoded, capped
  at ~2 MB decoded (→ 400 `"Photo too large"`), and stored with an
  inferred/most-likely content-type (default `image/jpeg`). Empty/invalid base64
  → 400. Absent → unchanged.
- **`AttendanceService.getPhoto`** — loads the record (404 if missing), enforces
  access (record's own employee **or** a management-role user in the **same
  company**; else `AccessDeniedException` → 403), returns bytes + content-type
  (404 if the record has no photo). Tenant-scoped.
- **`AttendanceController`** — passes `photoBase64` through; new
  `GET /api/attendance/records/{id}/photo` streaming the bytes with the stored
  `Content-Type` (private cache header).
- **`LiveStaffRow`** — added `UUID attendanceRecordId` (null until clocked in) +
  `boolean hasPhoto`, so the app can build the photo URL for today's rows.

## Endpoint shapes
- `POST /api/attendance/clock-in` body → `{ latitude?, longitude?, photoBase64? }`;
  returns the existing `TodayStatusResponse`.
- `GET /api/attendance/records/{id}/photo` → image bytes, `Content-Type` set.
  Auth required (401 unauthenticated); owner or same-company admin only (else
  403); 404 if the record/photo is missing.
- `GET /api/admin/attendance/live` rows gain `attendanceRecordId: uuid|null` +
  `hasPhoto: boolean`.

## Tests
`attendance/ClockInPhotoIT` (8 cases): store on clock-in + visible on live board;
GET as self → 200 (bytes round-trip, `image/jpeg`); as same-company admin → 200;
data-URL prefix stripped + `image/png` inferred; other-company owner **and**
other-company employee → 403; unauthenticated → 401; oversized → 400; no-photo
clock-in still 200 and its photo GET → 404. Uses a 4-byte JPEG marker
(`/9j/2Q==`) so it's fast.

## Gate
`./scripts/test-backend.sh` → **BUILD SUCCESS**, 69 tests, 0 failures (app boots
under `ddl-auto: validate`, proving the `bytea` mapping + V13 line up). Dev
Docker stack untouched.

## Notes / follow-ups
- Because lazy `@Basic` isn't enhanced in this build, the live-board query still
  materialises the photo bytes into each loaded entity. Volume is small (a
  company's employees clocked-in today) so it's fine for v1; a projection query
  that omits the bytes is a possible optimisation if the board is polled hard.
- App-side wiring (expo-camera capture, live-board thumbnail, Profile fake-data
  fix) is out of scope here and ships with the batched preview build.
