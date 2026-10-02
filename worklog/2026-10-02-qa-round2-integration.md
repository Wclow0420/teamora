# 2026-10-02 — QA round 2: integration

Companion to `2026-10-02-qa-round2-backend.md` and `2026-10-02-qa-round2-app.md`.

## Found while integrating
- **Work start time read back as 01:30.** V18 created `work_start_time` as SQL `TIME`
  with default `'09:00'`. With `hibernate.jdbc.time_zone=Asia/Kuala_Lumpur` the value
  goes through a `java.sql.Time` conversion using the 1970 offset (+7:30), so rows that
  got the column default (every existing company) read back as 01:30. The ITs passed
  because they only read values Hibernate itself wrote (symmetric conversion).
  Fix: **V19** converts the column to `VARCHAR(5)`; `common.HhMmConverter` maps it.
- **Home hero shift label** was a hard-coded "9:00 AM – 6:00 PM". It now says
  "Work starts {company work start time}" — the only shift fact we actually store.

## Follow-ups from the QA-routine review (also done here)
- Admin reset password: confirmation step; cannot be used on your own account (400);
  the employee's push tokens are deleted so signed-out devices stop getting alerts.
- "Signed out on your other devices" copy → "will be signed out shortly" (access
  tokens live up to 30 min after refresh tokens are revoked).
- `AuthedImage` retries once after forcing a token refresh, so receipt/selfie thumbnails
  recover after the access token expires.

## Verified
- Backend gate: 199 tests green. `npm run typecheck` clean.
- Simulator (iPhone 16e, Expo Go): staff payslip empty state, attendance month stepper,
  staff profile; admin dashboard bell → Notifications, Live Attendance (real list, working
  filters, no fake map), Company settings work start time + late grace.
- Not exercised on screen: offline retry screen, leave over-balance/overlap messages,
  payroll error alerts, reset-password confirm, camera capture.

## Still open
- Owner locked out has no recovery path (needs email reset).
- Camera permission string mentions only the clock-in photo (needs a native build).
- No second clock-in after clocking out; salary/hours cannot be cleared back to default.
- Demo data: the seeded June payslips are IN_REVIEW, so staff see none until a run is approved.
