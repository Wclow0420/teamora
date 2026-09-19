# 2026-06-16 — Sticky + collapsing header component

## What we built

A reusable screen frame, `CollapsingHeaderScreen`
(`src/components/layout/CollapsingHeaderScreen.tsx`), that keeps the page header
pinned to the top while the body scrolls. Two modes from one component:

- **Sticky** (default) — header fixed at full size; only the body scrolls.
  Implemented with RN `ScrollView` `stickyHeaderIndices={[0]}`. An optional
  `headerExtra` slot pins extra controls under the title row.
- **Collapsing** (`collapsible`) — header stays pinned *and* shrinks on scroll:
  the title font scales down, the eyebrow/subtitle fade + collapse their height,
  and the accessory (avatar/badge) scales down. Implemented with a Reanimated
  `useAnimatedScrollHandler` driving `interpolate` on header height + element
  styles. Content top padding equals the expanded header height, so the body
  meets the header exactly through the collapse, then slides under it.

Props: `title`, `subtitle`, `eyebrow`, `accessory` (+`onAccessoryPress`), `back`,
`large`, `titleSize`, `collapsible`, `headerExtra`, `bottomInset`, `paddingX`,
`background`, `barStyle`, `children`.

## Where applied

- **Collapsing**: staff Home (greeting + name + avatar shrink), admin Dashboard
  ("Good morning, Sarah" + subtitle + avatar shrink).
- **Sticky**: admin Live, Staff, Approvals, Payroll.
- **Sticky + headerExtra**: admin Schedule — the day selector and the
  "Tuesday, 17 June / 6 on shift" row are pinned in the header; only the shift
  list scrolls (exactly the requested behaviour).

These screens dropped `Screen` + `ScreenHeader` in favour of the new frame; the
body content was moved verbatim into `children`.

## Notes

- Designed as the standard way to add pinned/collapsing headers going forward —
  documented in CLAUDE.md §5 (component table) and §6 (navigation).
- Collapsing heights are derived from safe-area insets + whether an
  eyebrow/subtitle is present; override via `titleSize` if needed.

## Rollout to remaining staff screens

Converted the rest of the staff tab screens to the sticky header for
consistency (all `bottomInset={70}`, `badge` → `accessory`):
- **Attendance** — "Attendance" + month chip pinned; stats/chart/day-list scroll.
- **Calendar** — "Calendar" + Month/Week toggle pinned; month grid + upcoming scroll.
- **Payslip** — "Payslip" + month chip pinned; net hero + breakdown scroll.
- **Profile** — "Profile" + edit button pinned; identity/stats/menu scroll
  (logout wiring preserved).

Every staff + admin screen now uses either the sticky or collapsing header.

## Verification

- `npx tsc --noEmit` → clean.
- `npx expo export --platform ios` → bundles successfully (Reanimated worklet
  header animates on the UI thread).
