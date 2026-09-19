# 2026-06-22 — Real admin Dashboard + Home summary (kill the last fake data)

## Why
The admin Dashboard and the staff Home "Today's summary" were the last screens
showing **hard-coded sample numbers** — e.g. a fixed `/156` headcount, a fake
`91%` week chart with invented bars, a canned activity feed, and Home tiles like
"1 left / Break" for a feature that doesn't exist. Showing a manager fake stats
is actively misleading. Per the operating mandate (CLAUDE.md §0: *never show fake
data*), these are now backed by real data.

## Backend — new `dashboard` feature (read-only, no migration)
`GET /api/admin/dashboard` → `DashboardResponse` (new `com.teamora.dashboard`
package; secured by the existing `/api/admin/**` rule → OWNER/HR_ADMIN/MANAGER).
Pure aggregation over existing entities — **no schema change**, so `ddl-validate`
is untouched. It reuses the per-feature services rather than reimplementing:
- `headcount` — active employees in the company.
- `presentToday` / `onLeaveToday` — today's attendance (Asia/Kuala_Lumpur);
  shares `liveBoard`'s present-status logic via an `isPresent` helper.
- `pendingApprovals` — sums the **role-routed** `pending(caller)` from Leave +
  Claim + Overtime services (MANAGER → reports only; HR_ADMIN/OWNER → all).
- `payrollDueLabel` — current month's net total (`payrollService.summary`),
  falling back to the latest run, then `"0.00"`.
- `week` — distinct present employees per day for the current ISO week (Mon..Sun)
  + `max` (= headcount) + a `rateLabel` (% so far this week).
- `activity` — 6 newest items merged from recent leave/claim/overtime (by
  `updatedAt`), mapped to `{type, text, timeLabel, accent}`.

New repo finders: `AttendanceRepository.findAllByWorkDateRange`, and
`findRecentByCompany(companyId, Pageable)` (employee fetch-joined) on the leave/
claim/overtime repos.

**Tests:** `DashboardIT` — 200 for HR_ADMIN (asserts real shape: headcount > 0,
7-length week arrays, `max == headcount`) + MANAGER 200, EMPLOYEE 403, unauth
401. `./scripts/test-backend.sh` → **BUILD SUCCESS, 38 tests**, nothing broken.

## App
- `src/api`: `DashboardSummary`/`DashboardActivity` types, `dashboardApi.summary`,
  `useDashboard` hook (60s refetch).
- `app/admin/(tabs)/dashboard.tsx` — fully data-driven: dropped `@/data/mock`
  `ADMIN`; header now uses `useMe()` + a **time-aware greeting** and the real
  date; KPIs/chart/feed from `useDashboard`, gated by `AsyncBoundary` with an
  honest empty state ("No activity yet"); today's bar is highlighted. Removed the
  fake chevrons that implied the KPI cards were tappable (they weren't).
- `app/(staff)/(tabs)/home.tsx` — "Today's summary" tiles are now real (today's
  attendance word, annual-leave remaining, pending-claims total), composed
  client-side from existing hooks. Hero uses the real `shift` field instead of a
  hard-coded "6:00 PM"; greeting is time-aware.

## Clock-in screen + mock retirement
- `app/(staff)/clock-in.tsx` was the last `@/data/mock` consumer and showed a
  hard-coded `9:02 AM`, a fake date, and a false "120m · Inside geofence" claim
  (there is no geofencing). Now: a **live clock** (10s tick), the real long date,
  and the employee's real registered location (`useMe().location`) with an honest
  subtitle. Dropped the green "inside geofence" check. (The face-scan viewfinder
  is intentional prototype UI per golden rule §6 — left as-is, flagged below.)
- With its last consumer gone, **`src/data/mock.ts` was deleted** (fully
  orphaned dead code) and the `data/` line removed from CLAUDE.md §3.

## Verification
- `npm run typecheck` clean.
- Backend test gate green (38 tests).
- Live smoke (dev API rebuilt, V-state unchanged): `/api/admin/dashboard` →
  **401** unauth, **403** as `amir@lumi.com` (EMPLOYEE), **200** as
  `sarah@lumi.com` with real numbers (headcount 10, 7 present today — today is
  Monday so only Monday's bar is populated, 70% rate, real Amir activity feed).

## Notes / follow-ups
- `payrollDueLabel` carries 2 decimals (e.g. `47,237.10`) because it reuses the
  payslip money formatter; the app prefixes `RM `. Consistent with the existing
  payroll screens. Revisit if a whole-ringgit KPI reads cleaner.
- Home's "Today's summary" has no dedicated endpoint by design (cheap client
  composition). If we later want a single staff-home summary call, add one.
- **Clock-in "face scan" is theater** — the viewfinder + "Scanning your face…"
  imply biometric verification that doesn't happen (the button just calls
  `/clock-in`). Either build real face/biometric verification or soften the copy
  to an honest framing. Product call — left intact for now to preserve the
  approved design.
