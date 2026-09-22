# 2026-09-22 — Company calendar & holiday management: integration

Admin CRUD for `company_events` (backend) + admin calendar screens (frontend),
built by subagents. This entry covers integration and the frontend half (the
backend half has its own entry).

## Why this mattered
`CompensationService` already pays public holidays by reading `company_events`
where `event_type = HOLIDAY`, but `/api/calendar` was **read-only** — holidays
existed only as demo seed data. Real companies had no way to add them, so
holiday pay was dormant and the staff calendar couldn't be populated. This
activates both. **No migration and no native dependency** (the V6 table and
entity are unchanged), so it is OTA-deliverable.

## Frontend
- `src/api`: `CompanyEventItem` / `EventTypeValue` / create+update bodies;
  `calendarAdminApi` (list/create/update/remove); `useAdminCalendarEvents` +
  create/update/delete mutations, each invalidating BOTH `['adminCalendar']` and
  `['calendar']` so the staff calendar refreshes immediately.
- New screens `app/admin/calendar-events.tsx` (month-stepped list, tinted date
  tiles, type chips, empty state) and `app/admin/calendar-event-edit.tsx`
  (title / date / type / time label, delete with confirm). Registered in
  `app/admin/_layout.tsx`; linked from the admin profile menu as "Company calendar".
- Holiday type shows: "Public holidays are paid and are excluded from
  unpaid-leave deductions in payroll."

## Verified (dev, end-to-end via API)
- Backend gate → BUILD SUCCESS, **82 tests** (7 new CalendarAdminIT), up from 75.
- Jar hot-swapped into the dev container (no migration needed this round).
- POST holiday → 201 with the exact DTO the app expects
  (`{id,title,eventDate,eventType,accentColorKey,iconName,timeLabel}`);
  appears in the admin list **and in the staff `GET /api/calendar`**.
- RBAC: MANAGER mutation → 403; unauthenticated → 401.
- Validation: `BIRTHDAY` type → 400; blank title → 400.
- PATCH → 200; DELETE → 204 and it disappears from the staff calendar.
- Test event deleted afterwards — dev data clean. `npm run typecheck` clean.

## Notes / small judgement calls
- `timeLabel` semantics: backend treats `null` as "unchanged" and `""` as
  "clear"; the edit screen sends the trimmed string so clearing works.
- No `trash` icon in the typed icon set — delete uses `x` in `palette.danger`.
- Pre-existing inconsistency (out of scope, flagged): the staff
  `GET /api/calendar` does not validate a garbage `month` param, while the new
  admin GET returns 400. Worth aligning later.

## Delivery status
OTA-deliverable. Native batch still pending owner go: expo-location (geofence),
expo-camera (selfie), expo-file-system (statutory export).
