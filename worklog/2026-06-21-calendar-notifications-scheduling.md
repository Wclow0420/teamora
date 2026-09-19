# 2026-06-21 — Calendar, Notifications & Scheduling (the data gaps)

Filled the last three screens that were still on mock data, each with a new
backend domain (built in parallel) + app wiring.

## Backend (3 new domains, migrations V5–V7)
- **Scheduling** (`com.teamora.schedule`, V5): `shifts` table; `ShiftType`
  (MORNING/EVENING/REMOTE/OFF). `GET /api/admin/schedule?weekStart=` returns the
  week roster (7 days, shifts per day, on-shift counts); `POST` upserts a shift.
  Seeds the current week for ~6 Lumi staff. `ScheduleIT`.
- **Calendar** (`com.teamora.calendar`, V6): `company_events` table
  (HOLIDAY/EVENT/TOWNHALL/BIRTHDAY). `GET /api/calendar?month=YYYY-MM` aggregates
  company events **+ the caller's approved leave** into per-day dot colours, plus
  an upcoming list. Seeds events in the current month. `CalendarIT`.
- **Notifications** (`com.teamora.notification`, V7): `notifications` table with
  a backdatable `occurred_at`. `GET /api/notifications` (Today/Earlier split +
  relative time + unread count); `POST /api/notifications/read-all`. A reusable
  `NotificationService.create(...)` is ready for future event wiring (not called
  yet). Seeds 5 for Amir. `NotificationIT`.

## App
- API: types/endpoints/hooks for all three (`useCalendar`, `useNotifications` +
  `useMarkAllRead`, `useSchedule` + `useAssignShift`).
- **Calendar** screen: grid computed from the month with working prev/next, live
  dots (incl. approved-leave overlay) + upcoming list.
- **Notifications** screen: Today/Earlier from the API; "Mark all read" wired.
- **Schedule** screen (admin): real week roster with a working day selector;
  reached from the dashboard quick link.

## Verification
- Backend: `./scripts/test-backend.sh` → **32 tests, BUILD SUCCESS**. Fresh DB:
  V5–V7 migrate, seeders run (6 shifts, 4 events, 5 notifications). Live checks:
  calendar dots + upcoming, notifications today/earlier + unread, week roster.
- App: `tsc` clean + iOS bundle builds.

## Follow-ups
- The Schedule "+" (assign-shift form) and Calendar Month/Week toggle aren't built
  yet (read paths done; `useAssignShift` exists). Notification auto-generation on
  approvals is wired-ready (`create(...)`) but not yet called. Remaining static
  bits: Home "Today's summary" + dashboard week chart/activity feed.
