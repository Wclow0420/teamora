# Partial-day leave (half-day & hourly) — app side

Frontend half of the partial-day leave feature (backend ships the V15 migration,
`LeaveDurationCalculator` and the fractional compensation engine separately).
No native dependency, no `app.config.ts` change — OTA-deliverable.

## What changed
- **`src/api/types.ts`** — new `LeaveDurationUnit` (`FULL_DAY | HALF_DAY | HOURS`)
  and `HalfDayPeriod` (`AM | PM`). `ApplyLeaveBody` gains `durationUnit`,
  `halfDayPeriod?`, `hours?`, `startTime?` (all optional so an older server that
  defaults to FULL_DAY still accepts the payload). `LeaveRequest` / `PendingLeave`
  gain `durationUnit`, `halfDayPeriod`, `hoursLabel`. Balance `used/entitled/
  remaining` stay numbers — they're just fractional now.
- **`src/lib/numbers.ts`** (new) — `formatDecimal` / `formatDays` / `formatHours`.
  One place that trims trailing zeros so fractional balances never render as
  "12.5000" and unpaid days read "0.5 days".
- **`src/components/ui/TimeField.tsx`** (new) — optional time picker mirroring
  `DateField`, reusing the already-installed `@react-native-community/datetimepicker`
  in `mode="time"`. Exports `toHHMM` for the API. Exported from the UI barrel.
- **`app/(staff)/leave-apply.tsx`** — a **Duration** `SelectChips`. Full day keeps
  the start+end dates; Half day shows one date + an AM/PM picker; Hours shows one
  date + a decimal-pad hours field (with a live "2 hours = 0.25 day of your
  balance" hint derived from `useMe().hoursPerDay`, fallback 8) + an optional start
  time. The end-date field is hidden for partial units and `endDate === startDate`
  is sent. Client-side we only guard the obvious (hours > 0, hours <= hours/day);
  the real rules (single date, working day, not a holiday/rest day) are the
  server's and its `ApiError` message is surfaced inline.
- **`app/(staff)/leave.tsx`** — balances formatted + tabular-nums ("12.5 / 16").
- **`src/components/approvals/ApprovalCards.tsx`** — the leave card's date row now
  carries a duration chip, amber-tinted when the request is a half day / hours, so
  an approver can't mistake a 2-hour slice for a day off.
- **`app/(staff)/(tabs)/payroll.tsx`** — the unpaid-leave line falls back to
  `formatDays` when the server sends no label.

## Notes for the next person
- The unpaid-leave warning on the apply form now says "for the time taken" rather
  than "for the days taken" — partial leave means it isn't always whole days.
- `hoursPerDay` is the employee's *effective* value (own override else company
  default) already resolved by the backend on `EmployeeResponse`.
- Gate: `npm run typecheck` clean.
