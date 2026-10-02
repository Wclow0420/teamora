# QA round 2 — app fixes (F1–F13)

Frontend half of the round-2 QA spec; the backend half is in
`2026-10-02-qa-round2-backend.md`. OTA-safe: no new native modules, no
`package.json` changes.

## What changed and why

- **Sessions (F1/F2).** `AuthContext` clears the React Query cache on sign-out and
  before sign-in / sign-up, so one account never sees another's cached data.
  Session restore now only ends the session on a 401/403; a network or 5xx
  failure keeps the tokens and reports a new `offline` status, which
  `app/index.tsx` renders as "Can't reach Teamora" + Try again. The API client's
  token refresh got the same treatment — an unreachable server during refresh no
  longer wipes the tokens (it throws a retryable 503 `ApiError` instead). A
  non-JSON error body (proxy HTML page) no longer crashes the parser.
- **No silent failures (F3).** Approve/Decline (both approvals screens, via the
  new `useApprovalDecisions` hook), payroll Run / Approve / Mark-paid and
  "Mark all read" now alert the server's message. `src/lib/errors.ts` holds the
  shared `errorMessage` / `alertError`.
- **Honest payslip (F4).** The hero pill and the PDF share
  `payslipStatusLine` (`src/lib/payslip.ts`): "Paid 28 Jun · Maybank ••1234" only
  when paid and a bank is on file, "Approved · pay date …" otherwise; never
  "null". Drafts are filtered out client-side too.
- **Live attendance (F5).** Fake floor map, pins and "Bangsar South HQ" removed.
  Filters really filter, using the same grouping the backend counts with
  (`src/lib/liveAttendance.ts`). Rows show department · real site (each only when
  present) and a readable status instead of the raw enum. Stat tiles show "—"
  until loaded, not 0.
- **Period controls (F6).** New `MonthStepper` kit component replaces the dead
  chevron chips: staff Attendance steps months via `?month=`; staff Payslip steps
  through the employee's real payslips. Attendance's day column showed the raw
  ISO date — now the day of the month. "This week" chart is hidden on past months
  (the API always returns the current week).
- **Admin notifications (F7).** Bell with a real unread dot on the dashboard
  header → `app/admin/notifications.tsx`. The feed is now the shared
  `NotificationsScreen`; a push tap opens the right app's route.
- **Accessibility (F8).** Labels/roles on back buttons, bell, plus buttons,
  search clear, month arrows, selfie thumbs, log out, decline, approval tabs.
  `CollapsingHeaderScreen` gained `accessoryLabel`.
- **Forms (F9–F12).** Company settings: work start time + late grace (shown only
  when the API sends them), inline errors for an empty working-days mask and a
  blank company name. Leave apply: shows the real balance and blocks an
  over-balance request; server 400s (overlap / balance) shown inline. Passwords:
  one rule in `src/lib/password.ts` (min 8) with helper text on all four screens.
  Admin employee Profile edits Staff ID and Phone.
- **Admin payroll line (F13).** "Unpaid · 2 days − RM …" from the new line fields.

## Non-obvious

- The leave pre-check (`useLeaveBalanceCheck`) is deliberately conservative: it
  only blocks when its day count is exact (rest days + public holidays skipped,
  range within two calendar months, request in the same calendar year as the
  balance's leave year). It does not know about days held by pending requests —
  the server does, and its message is shown as-is.
- `employee.location` (a free-text string) is no longer used as a work-site
  fallback; only `workLocationName` is.

## Not done / follow-ups

- Admin Payroll still only shows the current month (no dead control there, so
  nothing was removed; a period stepper would be a feature).
- Pre-existing raw hex values remain in `clock-in.tsx`, `claims.tsx` and admin
  `payroll.tsx`.
- Not run on a device in this pass — verified by `npm run typecheck` only.
- CLAUDE.md not yet updated for `MonthStepper`, `admin/notifications` and the
  `offline` auth status.
