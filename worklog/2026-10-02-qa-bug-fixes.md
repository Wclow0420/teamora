# 2026-10-02 — QA bug fixes (frontend only, OTA-safe)

Fixes for a QA pass on the iOS simulator against the live dev backend. App-only
(`app/`, `src/`): no backend change, no new dependency, no `app.config.ts` change —
shippable as an EAS Update.

## Bugs

**1. Home hero was hard-coded to "Currently working".** The hero always rendered a
running timer + "Clock Out", even for `{"status":"ABSENT","clockInAt":null}`, and Home
had no way to clock in. It now lives in `src/components/attendance/AttendanceHero.tsx`
(screens stay thin) with three states read straight from `TodayStatus`:
- `clockInAt == null` → "Not clocked in yet", the shift, primary **Clock In** → `/clock-in`.
  If `status` is `ON_LEAVE` the headline is "On leave today"; Clock In stays available
  (quieter `light` button) because half-day/hourly leave means people do still work.
- `clockInAt != null && workedMinutes == null` → "Currently working" + live timer +
  "In at h:mm" + **Clock Out**.
- `clockInAt != null && workedMinutes != null` → "Done for today" + static `Xh YYm`,
  static dot, no button.

Clock Out behaviour also changed: it used to fire the mutation *and* push the Clock In
screen (wrong screen, errors swallowed). It now asks to confirm, stays on Home, shows
"Clocking out…" while pending, and surfaces a failure in an alert. The attendance query
invalidates on success so the hero flips to "Done for today".

**2. Timer counted from screen mount.** `useLiveTimer` took a base in seconds built
from `workedMinutes` (null until clock-out → always 0) and incremented a counter. It
now takes the clock-in ISO instant and derives `now − clockInAt` from the wall clock on
every tick, re-syncing on `AppState` → `active`. The fake 6h24m default is gone. Home
was its only caller. Device clock behind the server clamps to 0 rather than going
negative.

**3. Wrong brand.** Wordmark "lumi" → "Teamora" on onboarding, login and register;
legal line → "Teamora's Terms & Privacy". No other product-level "lumi" strings exist
in `app/` or `src/` (only the demo e-mail addresses, left alone).

**4. Dead menu rows.** A chevron must lead somewhere.
- Staff profile — removed: Personal information, Employment details, Documents &
  contracts, App settings, Privacy & security, and the header pencil button (no
  handler). Kept/wired: Payslips → `/payroll`. Added three rows that have real
  screens so the card isn't a single row: Leave → `/leave`, Claims → `/claims`,
  Notifications → `/notifications`.
- Admin profile — removed: Personal information, App settings, Privacy & security,
  and the same dead header pencil. Company settings / Work locations / Company
  calendar / Team members were already wired. `href` is now required on the row type
  so a dead row can't be added by accident.

**5. Demo credentials on the login screen.** Now gated by `showInternalHints()` in
`src/lib/env.ts` (`extra.APP_ENV` via `expo-constants`). Fail-safe on purpose:
`app.config.ts` defaults APP_ENV to `development` when unset, so `development` is only
trusted together with `__DEV__`; `preview` must be explicit; anything else is
production. A release bundle published without APP_ENV therefore hides the line.

## Polish
- **P1** Leave balances are a 2-column grid (name on top, up to 2 lines, then
  `remaining / entitled days`) — no more "Emergenc/y Leave".
- **P2** `LeaveBalance.accrual` added to `src/api/types.ts`; `NONE` (untracked) types
  get no tile. They are also excluded from the Profile "Leave left" total and can't be
  picked as Home's fallback leave tile. "Leave left" now goes through `formatDays`
  (fractional balances no longer print float noise).
- **P3** Admin staff rows join only the parts that exist (job title / department) and
  render no line at all when both are empty.
- **P4** Home summary labels may wrap to two lines instead of truncating
  ("Claims pending"); values shrink-to-fit instead of clipping.
- **P5** Onboarding's striped `Placeholder` replaced by
  `src/components/onboarding/OnboardingIllustration.tsx` — a theme-composed preview
  (espresso worked-time card, "Leave approved" chip, "Payslip ready" row). No assets.
  It is decorative and hidden from screen readers; the "8:00:00" on the mini card is
  illustration, not user data.

## Theme
Added `onDark` to `src/theme/colors.ts` (text/eyebrow/dim/glow/live/idle on espresso)
so the new hero and illustration carry no inline rgba. `HeroCard` still has its own
inline copies — worth migrating to `onDark` later.

## Verification / honest gaps
- `npm run typecheck` clean.
- **Not visually verified by me**: the shared simulator was mid-session in the admin
  app, so I did not drive it. Home's three hero states, the leave grid, and the
  onboarding illustration need an eyes-on pass.
- Still open (not in this brief): "Forgot password?" on login is styled like a link
  but has no handler — same class of fake affordance as bug 4.
- Clocked-out state has no "clock in again" path; if the backend supports multiple
  sessions per day this needs a product decision.
