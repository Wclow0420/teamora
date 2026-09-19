# 2026-06-18 — Admin nav rework + admin Profile

## Why

UX feedback: the admin nav had no Profile tab, the top-right avatar logged you
out instantly (unintuitive), and Schedule was an odd dashboard button. Chosen
tab lineup: **Home · Staff · Approve · Payroll · Profile**, with Live + Schedule
reached from the dashboard.

## What changed

### Structure (mirrors the staff app)
`app/admin/` is now a **Stack** wrapping a `(tabs)` group + pushed detail screens:
- `admin/_layout.tsx` → Stack (`(tabs)` + `live` + `schedule`).
- `admin/(tabs)/_layout.tsx` → Tabs: dashboard, staff, approvals, payroll,
  **profile** (moved the 5 tab screens into `(tabs)/`).
- `admin/live.tsx` + `admin/schedule.tsx` → pushed screens (full-screen, native
  back, no tab bar). `live` gained a back button and dropped `bottomInset`.

### Admin Profile (new) — `admin/(tabs)/profile.tsx`
Mirrors the staff Profile: identity card (violet avatar, name, job title, company
chip), stats (Role label / Department / Staff ID), a settings menu (Company
settings, Team members, …), and the **Log out** action — which now lives here.

### Dashboard
- The top-right avatar now opens **Profile** (`router.push('/admin/profile')`)
  instead of signing out.
- Replaced the lone "View week schedule" button with a **Quick access** row →
  Live attendance + Week schedule (since they're no longer tabs).

## Verification
`npx tsc --noEmit` clean; `npx expo export` bundles. CLAUDE.md §3 structure
updated.
