# 2026-10-03 — Reusable animated Wizard + new-company setup flow

## What
- **Wizard kit** (`src/components/wizard/`): `Wizard` (steps as data — fixed header with
  back · springing progress bar · Skip, sliding/staggered steps, pinned primary button with
  spinner and an inline error), `WizardCelebration` (springing coral check that draws itself,
  a soft burst of dots/rings, then a checklist ticking in), `WizardProgress` (bar / dots) and
  `motion.ts` (the springs, stagger and step enter/exit worklets — one motion vocabulary).
  API is documented at the top of `Wizard.tsx`; CLAUDE.md §5 has the rows.
- **Kit additions** (`src/components/ui/`): `Stepper` (− value +, tabular, hold-to-repeat,
  VoiceOver adjustable), `ChoiceTile` (big option card), `CheckBox` (extracted from
  holiday-import, which now uses it), and `Button loading`.
- **New-company setup** (`src/features/setup/`, rendered by `app/(auth)/setup.tsx` and
  `app/admin/setup.tsx`): welcome → you → company (creates company + owner, signs in) →
  work week → leave → public holidays → workplace → team → finale. Every step saves on
  Continue, so nothing is lost by leaving early.
- **Entry points:** Onboarding "Get started" and Login "Create an account" → `/setup`;
  `register.tsx` is now a redirect. Dashboard shows a "Finish setting up your company" card
  for an OWNER whose `setupCompletedAt` is `null` → `/admin/setup` (starts at Work week,
  prefilled from the server).
- **API:** `CompanyResponse.setupCompletedAt?: string | null`,
  `companyApi.completeSetup()` → `POST /api/companies/me/setup-complete`,
  `useCompleteCompanySetup()`. `signUp(…, { deferPush: true })` skips the push-permission
  prompt so it doesn't land mid-wizard; the wizard registers for push when it ends.

## Why it's built this way
- **Staying in the wizard after sign-up.** `signUp` only flips auth `status`; nothing in the
  app navigates on a status change (the role redirect in `app/index.tsx` renders only at "/",
  the admin layout guard isn't mounted under `(auth)`). So the wizard calls `signUp` in step 3
  and just carries on. The route captures "was already signed in when opened" once (→ normal
  role redirect) so the mid-flow sign-in can't trip it. Swipe-back is disabled on both setup
  routes; Android hardware Back walks the wizard.
- **No way back to steps 1–3** once the account exists (`noBack`); the header shows × there,
  which offers "Finish later" → Dashboard (the card brings them back).
- **Step direction is a shared value** read inside the entering/exiting worklets, so the
  outgoing step always leaves the right way (a prop would be one render stale on unmount).
- **Reduce Motion:** all travel/scale is dropped and a short crossfade kept
  (`ReduceMotion.Never` on the fade — otherwise Reanimated skips it and steps hard-cut).
- **Keyboard:** `Screen` can't host a pinned footer, so the Wizard owns a
  `KeyboardAvoidingView` (the one sanctioned exception) and scrolls the focused field into
  view on `keyboardDidShow`.
- **Idempotent on a second pass:** the workplace step remembers the site it created and
  updates it (or deactivates it for "Anywhere") instead of adding a twin; holiday import
  skips dates already on the calendar; created team rows are never re-posted.
- **Team invites** generate a readable temp password client-side (`src/lib/tempPassword.ts`,
  `Kopi-4821-teh`) and stay on the step after creating so each login can be shared via the
  native share sheet. Invited staff are assigned to the site created in the workplace step
  (the geofence is per assigned site — otherwise "must be within 200 m" would be untrue).

## Deviations from the spec (deliberate)
- Leave note: the seeded defaults (16/14/5) are **not** the Employment Act minimums, so the
  copy says "common Malaysian defaults" and states the real minimums (8 annual / 14 sick for
  under 2 years' service) rather than claiming the numbers are the minimums.
- "Late after" stepper allows 0–120 (the server's range) instead of 0–60, so a resumed
  company with a larger saved value isn't silently clamped.
- `WizardStep` gained `noBack`, `hideProgress`, `bare`, `iconTone`, and `onContinue`
  receives the ctx (needed for `goTo('you')` on a duplicate email).
- `WizardCelebration` items take an optional `detail` ("12 added", "Skipped").
- Resume mode passes only the six setup steps (so progress reads 1/6…6/6) rather than
  `initialStep` on the full nine.

## Verified
- `npm run typecheck` clean; the running Metro (19001) bundles the app with the new code.
- Simulator (iPhone 16e, Expo Go, existing owner account, **resume** route via deep link):
  work week prefilled and saved (same values), stepper +/−, Back/forward with state kept,
  leave list, holidays list (rest of 2026 + 2027, default ticks, "Add 14 holidays"), Skip,
  workplace tiles + site form reveal, team "Add another", finale with correct checklist,
  × back to the Dashboard.

## NOT verified (needs the backend change + a human pass — QA cases AUTH-17…20, SETUP-01…09)
- The **signed-out** path (welcome → you → company → `signUp`) end to end.
- `POST /api/companies/me/setup-complete` and the dashboard card (backend not deployed when
  this was written; the card only shows when the field is strictly `null`).
- Keyboard behaviour (the simulator had the hardware keyboard on), holiday import, site
  creation with real GPS, team creation + share sheet, Reduce Motion, Android.

## To keep in mind
- Changing a leave type's default days doesn't rewrite balances that already exist (the
  owner's own balance is created at sign-up with the seeded default).
- If setup-complete fails, the finale shows the error and stays; nothing else is lost.

## Integration + hands-on check (owner session)
Backend: V27 `companies.setup_completed_at` (existing companies back-filled),
`CompanyResponse.setupCompletedAt`, `POST /api/companies/me/setup-complete`; 293 tests green.
Walked the signed-out path on the simulator end to end as a new company ("Kopi Test Co"):
welcome → about you → company (account created, wizard continues, no jump to the dashboard)
→ work week (Mon–Sat saved) → leave (Annual 15 saved) → 14 holidays added → Anywhere →
invited one person (temp password "Hujan-2152-bunga", login 200) → finale → dashboard;
`setupCompletedAt` set. Test company deleted afterwards.
Polish fixed: welcome mark's halo was clipped by the screen edge; work-week summary read
"Mon, Tue, Wed, Thu, Fri, Sat" → "Mon–Sat" (`describeMask` now collapses runs).
Not exercised on screen here: duplicate-email bounce, software-keyboard overlap, the
site + GPS branch, Share login sheet, Finish later → resume card, Reduce Motion, Android.
iOS shows its own "Save Password?" sheet after the account step — system behaviour.
