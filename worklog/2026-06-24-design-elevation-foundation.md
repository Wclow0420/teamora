# 2026-06-24 — Design elevation: foundation + flagship (anti-"AI-slop")

## Why
Feedback that the UI reads as AI-generated: generic gradients, visual glitches,
awkward rhythm. Rather than reskin screen-by-screen, fix it at the **root** (the
design system) and prove the new language on a flagship screen before rollout.

## Verification method (honest)
No iOS simulator in this environment, so live Reanimated frames can't be
screenshotted here. The reliable loop used: build a faithful **iPhone-resolution
HTML mock from the real tokens**, render it with headless Chrome (Figtree loads),
and critique actual pixels — the same trick that verified the payslip PDF. This
verifies the *visual language*; the RN screens are then built to match + typecheck.
Full per-frame audit still wants the running app (next: wire Expo web / device).

## Foundation (in code, typechecked)
- **Gradients demoted** (`colors.ts`) — retuned to tight single-hue "deepens",
  reserved for true hero surfaces; documented that wide ramps are the slop tell.
  Added `palette.espresso` (dark hero) + `surfaceSunken` (subtotal rows).
- **Typography** (`typography.ts`) — fixed cramped leading that clipped descenders
  (`h1` was 21/22, `h2` 20/20 → now on a 1.2–1.5× leading scale with intentional
  negative tracking). This was a literal rendering bug, not just taste.
- **Elevation** (`shadows.ts`) — soft, wide, low-opacity warm-brown ambient
  shadows; the coral CTA "glow" (0.4 neon) → a restrained brand-tinted lift.
- **`Button`** — dropped the coral **gradient**; now confident flat colour with a
  crafted press (scale + darker pressed fill) and soft ambient lift. Same API.

## New primitives
- **`HeroCard`** — the signature espresso hero: eyebrow + big **tabular-numeral**
  amount + optional status pill, one restrained coral glow, soft float shadow.
- (`EmptyState` shipped earlier in the day.)

## Flagship applied: staff **Payslip** screen
Rebuilt to the approved spec: espresso `HeroCard` net-pay (replacing the cream
gradient hero); **earnings** as icon-tile rows + a sunken "Gross pay" subtotal
(OT/claims/bonus rows appear only when non-zero — clean common month); **tile-less**
secondary-weight **deductions** (EPF/SOCSO/EIS/PCB) + a sunken "Total deductions"
subtotal; flat coral CTA; tabular numerals throughout. Math still reconciles.

## Verification
- `npm run typecheck` clean.
- Two design-board renders reviewed at 390px @2x; the refined spec (tile-less
  deductions, distinct subtotals, baseline "RM") is the reference the RN screen
  was built to.
- Design language recorded in CLAUDE.md §4 (rules) + §5 (HeroCard/EmptyState).

## Rollout progress (ordered by visibility)
1. ✅ **Payslip** (flagship) — done, above.
2. ✅ **Home** (staff) — coral-gradient attendance hero → espresso worked-time
   hero (live `LiveDot`, big tabular timer, in-hero Clock Out); quick-actions grid
   radius/rhythm tightened; summary tiles → sunken tiles with tabular numerals.
   Verified against a 390px render mock.
3. **Admin Dashboard** — refined KPI grid (tabular nums), chart, activity feed.
3. Attendance, Leave, Claims, Calendar, Notifications, Approvals.
4. Admin: Staff, Live, Schedule, Payroll-run, Employee forms.
5. Primitives second pass: `Chip`, `IconTile`, `SectionLabel`, `ScreenHeader`,
   `PillTabBar`, `Avatar` — refine to the elevated language (lifts all screens).
6. Motion pass: spring-based press/transitions, shared-element continuity.
Each screen: build to a rendered mock, apply in RN, typecheck.
