# Pay-setting overrides visible + admin payroll month stepper

## What
- **Backend:** `EmployeeResponse` now also returns the employee's raw stored pay
  settings — `payBasisOverride`, `workingDaysOverride`, `hoursPerDayOverride`
  (null = no personal value). The effective `payBasis` / `workingDays` /
  `hoursPerDay` are unchanged. No migration (the columns already exist).
  Assertions added to `EmployeeClearPaySettingsIT` (null after a clear, set after
  a PATCH and on the detail GET).
- **Admin → Compensation:** when an override is null the form opens on
  "Company default" for pay basis, with "Use company default" ticked for working
  days (the default days shown dimmed and locked — `WeekdayToggles` gained a
  `disabled` prop) and an empty hours field with the default as placeholder.
  Saving only sends a field that moved away from how it opened, so an untouched
  form sends nothing. Against an older backend (fields absent) it behaves as before.
- The second "Compensation" heading on that screen is now "Pay basis & schedule".
- **Admin → Payroll:** `MonthStepper` in the header; the run view, Run / Re-run /
  Approve / Mark-paid and the Reports & export link all use the selected month.
  Next is disabled at the current month; the stepper is locked while an action
  is in flight. The status chip moved from the header into the top of the body.

## Notes
- The override fields are filled on every `EmployeeResponse` path (list / me
  too), not just detail — they are plain column reads.
- Ticking "Use company default" no longer un-ticks itself when a day is tapped
  (days are locked while it is ticked); untick it to set a personal schedule.
- Not verified on a device in this change (typecheck + backend suite only).

## Hands-on simulator pass (same day)
Walked the staff and admin apps on the iPhone 16e simulator against the dev backend.
Found and fixed:
- **Staff calendar put every date on the wrong weekday.** Cells were `${100 / 7}%` wide;
  seven of them overflowed the row by a rounding hair, so the 7th wrapped and the grid
  became six columns (3 Oct 2026, a Saturday, showed under Sunday). Now a fixed
  `14.2857%` (rounded down).
- Clock-in card for staff with no assigned site said "Your work location" under the
  company name → "No site assigned — you can clock in from anywhere".
- Home quick action labelled "Schedule" opened the calendar → "Calendar".
- Onboarding claimed "Face + GPS check in under 2 seconds" (there is no face
  recognition) → "A quick selfie and location check at your workplace".
Confirmed working on screen: clock in again (timer net of break, attendance row 7m),
leave apply (rest-day, over-balance and overlap all refused with clear messages),
overtime form, claims form, notifications, change password, payroll month stepper
(Sep draft run), compensation "company default" state.
Still not exercised on screen: completing a password reset/change, payroll
run → approve → mark paid, approvals decide, camera capture (no camera on simulator),
the offline retry screen. The demo notification seed still mentions "Bangsar South HQ".

## Second pass — admin
- **Decline button was a sliver.** `Button` is full-width by default, so in the approval
  cards it took the whole row and squeezed Decline to an "×". The flex share now sits on
  a wrapper `View`. Affected every leave / claim / overtime card in both apps.
- Admin tab title "Approve" → "Approvals"; pending-leave label "No balance" (read as
  zero days left) → "Balance not opened yet".
- Verified: approve a leave on screen (count 7 → 6, balance 12 → 11.75 for 2 hours);
  payroll approve is 403 for a manager, works for HR, and the employee then sees the
  payslip; September run visible through the month stepper.
- Dev data changed by this testing: Amir's 2 Oct 2-hour leave is now approved, the
  September 2026 payroll run is APPROVED, and Amir has a 3 Oct attendance record.
