# 2026-10-03 — Hands-on simulator pass 2 (admin flows)

Second walk through the admin app on the iPhone 16e simulator against the dev backend.

## Found and fixed
- **Data never refreshed.** React Query's window-focus refetch means nothing in React
  Native, so a screen kept whatever it first loaded: payroll still said "Draft" 45
  minutes after the run was approved elsewhere. New `src/api/QueryFreshness.tsx`
  refetches mounted, stale queries when the app returns to the foreground and on every
  route change.
- **Server "today" was UTC.** `LocalDate.now()` / `YearMonth.now()` /
  `LocalDateTime.now()` ran in the container's UTC zone, so from midnight to 8 a.m.
  Malaysian time the calendar's "today", the leave year/accrual date, the schedule week,
  claims month-to-date and the export timestamp were a day (or 8 hours) behind. All now
  use `common.Zones.KL`.
- **Statutory export rows had no labels.** Same full-width `Button` bug as the approval
  cards: the Export button swallowed the row, hiding which file was which.
  `block={false}`. Audited every `<Button>` inside a row — this was the last one.
- **Decline was one tap, no confirmation,** right beside Approve. It now asks first
  (leave, claims, overtime; both apps). Approve stays one tap.
- Scrolled content slid under the status bar on every `Screen` page → the top inset now
  sits on the frame.
- Export subtitle showed "2026-09" → "September 2026". Schedule week label "28–4 October"
  → "28 Sep – 4 Oct"; the schedule opens on today rather than Monday.

## Confirmed on screen
Decline (with confirm, count 6 → 5), Run payroll for October (11 payslips), statutory
summary + four export rows, work locations list, company calendar (empty state), add
employee form, scheduling.

## Not exercised on screen
Typing through add-employee / company-settings saves, the share sheet after Export,
Mark as paid, the manager's inbox, password change/reset completion, the offline
screen, anything needing a camera or real GPS.

## Product gaps noticed (not built)
- The company calendar starts empty: Malaysian public holidays are not preloaded.
- Decline takes no reason, so the employee is not told why.

## Dev data changed
October 2026 payroll run created (draft); Amir's 22 Jun emergency leave declined.

## Pass 3 — save flows (same day)
- **Company profile edits were never saved (since day one).** `CompanyService.update`
  mutated the caller's company taken from the security context — a detached entity with
  `open-in-view: false` — so the PATCH echoed the change but nothing was flushed.
  It now loads a managed copy; the IT reads the value back with a fresh GET.
- **Add employee had no start date**, so mid-year hires got a full-year entitlement
  (16 days instead of 4 in October). The form now has "Start date" (default today). The
  API's "no join date = no proration" rule is unchanged (tests rely on it).
- Temporary password on Add employee was masked → iOS offered to save it to the
  *admin's* keychain. Now plain text, like Reset password.
- Leave entitlement: "3.50" vs 3.5 counted as a change (and would have set an override
  flag); untracked types (Unpaid) are hidden; wording now explains proration + overrides.
- Employee hub Profile tile said "Not set" when only staff ID / phone existed.
Verified on screen: add employee (validation + success → hub), Profile save incl.
duplicate staff ID error, Employment save (manager + work location), join-date
re-proration (16 → 4), Statutory & bank save, Compensation save (override shown),
Leave entitlement override (flag set only on the changed type).
Dev data: test employee "QA Tester" (qa.tester@lumi.com) created; Lumi company phone set.
Also in pass 3:
- Payroll: Approve / Mark as paid / Reports moved above the per-employee breakdown (they
  were below every payslip — a long scroll for a real company).
- Staff Profile "Leave left" summed every leave type (35 days for Nadia: annual + medical
  + emergency + study) → now "Annual left", matching Home. Home claims tile lacked "RM".
Verified on screen: new calendar event, new leave type (appears in the staff picker),
Mark as paid (employee sees "Paid 30 September"), admin change password (old rejected,
new accepted — demo password restored), log out, manager login + approvals inbox,
leave application on a weekday (owner notified), overtime, claim without receipt.
Verified by API: forgot-password reset (wrong 400, right 204, reuse 400, new password works).
Dev data: Nadia has a pending 15 Oct annual leave, a 2.5h overtime and a RM 23.50 claim;
"QA Townhall" event on 3 Oct; "Study Leave" type; September payroll PAID;
qa.tester@lumi.com password is now newpass123.
