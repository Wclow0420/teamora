# 2026-10-03 — Round 3: password recovery, clock in again, clear-to-default

## What
- **Forgot password by one-time code.** `POST /api/auth/forgot-password` (always 200, no
  account enumeration) creates a 6-digit code: BCrypt-hashed, 10-minute expiry, 5 wrong
  attempts, max 3 requests per 15 min, single use. `POST /api/auth/reset-password` sets
  the password, revokes refresh tokens and deletes push tokens. New screen
  `app/(auth)/forgot-password.tsx`.
- **Delivery is a seam, not a decision.** `PasswordResetSender` has one implementation,
  `LoggingPasswordResetSender`. No email provider has been chosen. For local dev,
  `PASSWORD_RESET_EXPOSE_CODE=true` returns the code as `devCode` and the app shows it in
  a "Dev only" note. Default is false; `.env.example` ships false.
- **Production does not expose the flow yet.** Login's "Forgot password?" opens the code
  flow only when `showInternalHints()` is true (dev/preview); otherwise it keeps the
  "ask your HR admin" alert — a real user would never receive a code.
- **Clock in again.** V20 adds `attendance_records.break_minutes`. Clocking in after a
  clock-out resumes the record: the gap becomes break time, first clock-in time / status /
  photo are kept, geofence re-applies. Worked minutes are net of breaks. Home shows
  "Clock in again" and the timer subtracts breaks.
- **Clear pay settings to the company default.** `clearMonthlySalary`, `clearWorkingDays`,
  `clearHoursPerDay`, `clearPayBasis` on `PATCH /api/employees/{id}` (value + its flag →
  400). The compensation form now sends only what changed.
- **Camera permission text** now mentions claim receipts (takes effect next native build).

## Verified
- Backend gate 225 tests green; typecheck clean; V20 applied on dev.
- Simulator: forgot-password step 1 → step 2 with the dev-only code shown.
- Not exercised on screen: completing a reset, clock in again, the compensation
  "use company default" controls.

## Open
- Pick an email provider, implement a real `PasswordResetSender`, then un-gate the flow.
- `EmployeeResponse` returns effective pay values only, so the form cannot show which
  fields are personal overrides.
