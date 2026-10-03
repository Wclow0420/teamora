# 2026-10-03 — Malaysian public holiday import + decline with a reason

## What
- **Public holidays (suggest, admin confirms).** Catalogue file
  `backend/src/main/resources/holidays/my-public-holidays.json` (rest of 2026 + all of 2027,
  from publicholidays.com.my, not the official gazette). `GET
  /api/admin/calendar/holiday-suggestions?year=` returns the list with `alreadyAdded`;
  `POST /api/admin/calendar/holiday-import` creates HOLIDAY events, skipping dates that
  already have one. App: `app/admin/holiday-import.tsx` (year chips, ticks; state-specific
  rows start unticked with their note), entry card on Company calendar. Never automatic:
  dates move yearly and differ by state, and HOLIDAY rows affect pay.
- **Decline reason.** V21 adds `decision_note` to leave, claims, overtime. Reject endpoints
  take optional `{ reason }` (≤300). Employee notification reads "… was declined: reason";
  staff lists show "Reason: …". Decline now opens `DeclineSheet` (bottom sheet) instead
  of an Alert.
- Backend also: malformed JSON / bad ISO dates / wrong param types now 400 instead of 500;
  notification title/body capped to the column sizes.

## Found while testing on the simulator
- **Multiline text fields were only tappable on their first line** (the input was one
  line tall inside an 88px box). Affects every multiline field (leave reason, claim notes,
  decline reason). The input now fills the box.

## Verified
- Backend 232 tests green; typecheck clean; V21 applied on dev.
- Simulator: imported 13 holidays for 2027 (rows flip to "Added"); declined an overtime
  request with a reason via the sheet — employee's overtime list carries
  `decisionNote` and the notification reads "Your 1.5h overtime was declined: No stock
  count this month".

## To keep in mind
- **Yearly chore:** add the next year's holidays to the JSON once JPM gazettes them.
- Android keyboard behaviour of the decline sheet is unverified (no Android device here).
