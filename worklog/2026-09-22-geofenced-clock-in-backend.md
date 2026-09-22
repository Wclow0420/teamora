# 2026-09-22 — Geofenced clock-in (backend)

Server-side GPS geofencing for attendance clock-in. Companies define multiple
work locations (GPS pin + radius), staff are assigned to one (nullable), and the
backend re-validates the phone's coordinates against the assigned site's radius
on clock-in. App work is a separate follow-up (native `expo-location` dep → EAS
build); this entry covers `backend/**` only.

## What changed

### Migration
- `V12__work_locations.sql`
  - `work_locations(id, company_id, name, latitude NUMERIC(9,6), longitude
    NUMERIC(9,6), radius_m INT DEFAULT 100 CHECK (radius_m >= 50), active, timestamps)`
    + `idx_work_locations_company`.
  - `employees.work_location_id UUID` (nullable FK → `work_locations`).
  - `attendance_records.clock_in_lat / clock_in_lng NUMERIC(9,6)` for audit.
  - **No seeding** — demo employees stay UNASSIGNED so existing seeded
    attendance/payroll data and all prior tests are untouched.

### New `com.teamora.location` package
- `WorkLocation` (TenantEntity), `WorkLocationRepository` (company-scoped finders),
  `WorkLocationService` (create/update set + verify company; radius default 100),
  `WorkLocationController`, `dto/WorkLocationDtos` (Create/Update/Response;
  `MIN_RADIUS_M=50`, `DEFAULT_RADIUS_M=100`).

### `com.teamora.common.GeoUtil`
- `distanceMeters(lat1,lng1,lat2,lng2)` — Haversine, WGS-84 mean radius.

### Employee
- Added nullable `@ManyToOne WorkLocation workLocation` (join column
  `work_location_id`), following the V11 comp-field pattern.
- `EmployeeService.create/update` accept `workLocationId` (null clears, exactly
  like `reportingManagerId`); resolved + company-verified via
  `WorkLocationRepository`.
- `EmployeeResponse` adds `workLocationId` + `workLocationName` (read defensively
  with `Hibernate.isInitialized`; detail/list queries now `left join fetch
  e.workLocation`).

### Attendance
- `AttendanceService.clockIn(current, latitude, longitude)`: resolves the
  employee's active assigned site; if present, requires coords within `radius_m`
  (Haversine) else throws `BadRequestException` (→ 400 `{message}`) like
  `"You're ~120 m from Shop A. Move within 100 m to clock in."`; missing coords
  for a geofenced employee → 400 "Location required…". Unassigned or inactive
  site → allowed without coords. Stores `clock_in_lat/lng`; sets `location` to the
  site name when geofenced. Existing LATE/PRESENT logic preserved.
- `AttendanceController` clock-in accepts optional `@RequestBody ClockInRequest
  { latitude?, longitude? }` (validated ranges).

## API shapes (app must match)
- `GET /api/work-locations` (auth) → active sites:
  `[{ id, name, latitude, longitude, radiusM, active }]`.
- `GET /api/admin/work-locations` (management URL) → all incl. inactive.
- `POST /api/admin/work-locations` (OWNER/HR_ADMIN, 201) →
  `{ name, latitude, longitude, radiusM?, active? }`.
- `PATCH /api/admin/work-locations/{id}` (OWNER/HR_ADMIN) → partial, same fields.
- Employee create/update accept `workLocationId` (nullable → none); employee
  detail/list responses add `workLocationId`, `workLocationName`.
- `POST /api/attendance/clock-in` body `{ latitude?, longitude? }` (optional;
  effectively required for geofenced staff). Out-of-range/missing → 400
  `{ message }`.

## RBAC / tenancy
- `/api/admin/**` = OWNER/HR_ADMIN/MANAGER (URL); config mutations add
  `@PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")` (managers get 403).
- Every query company-scoped; company set on create, verified on update.

## Tests (gate: `./scripts/test-backend.sh` → BUILD SUCCESS, 61 IT + unit)
- `GeoUtilTest` — same-point 0, 1° latitude ≈ 111.19 km, KLCC→KL Tower ≈ 1.05 km,
  symmetry.
- `WorkLocationConfigIT` — 401 unauth, 403 for employee/manager on mutate, owner
  CRUD, `radiusM<50` → 400, blank name / missing coords → 400, active-only staff
  list vs full admin list, tenant isolation.
- `GeofenceClockInIT` — assigned inside → OK (location = site name); outside →
  400 w/ distance + site name; assigned w/o coords → 400; unassigned → OK w/o
  coords; inactive site → OK w/o coords. Uses fresh companies/employees so shared
  seeded attendance is untouched.

## Notes / deviations
- Placed the entity in a new `com.teamora.location` package (spec allowed
  attendance/company) to keep the geofence domain self-contained.
- Chose HTTP 400 (`BadRequestException`) for out-of-range, per spec's allowance
  (400 or 422); the app maps 400/422 to the `ApiError` `{message}`.
- Assigned-but-no-coords is rejected (400) rather than silently allowed, so
  geofenced staff can't bypass the check by omitting coordinates.
- Mock-location detection remains out of scope (v2).
