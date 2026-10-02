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
