# 2026-09-22 — Company calendar & holiday admin CRUD (backend)

`CompensationService` already pays public holidays by reading `company_events`
where `event_type = HOLIDAY`, but `/api/calendar` was **read-only** — holidays
only ever existed as demo seed data, so the holiday-pay logic was effectively
dormant for a real company and the staff calendar could not be populated. This
adds admin CRUD over `company_events` to activate both. App work is a separate
follow-up; this entry covers `backend/**` only.

## What changed

### No migration
The `company_events` table (V6) is unchanged and the `CompanyEvent` entity is
untouched — no new columns, so this ships on the existing schema (and the app
side is OTA-deliverable).

### `com.teamora.calendar`
- **`CalendarAdminController`** (new) — admin CRUD alongside the existing
  read-only `GET /api/calendar`:
  - `GET  /api/admin/calendar/events?month=yyyy-MM` → all company events in that
    month, earliest first. `month` omitted → current month; unparseable `month`
    → 400 (the staff endpoint still lets a bad param bubble — untouched here).
  - `POST /api/admin/calendar/events` → **201**.
  - `PATCH /api/admin/calendar/events/{id}` → 200.
  - `DELETE /api/admin/calendar/events/{id}` → **204**.
  - Reads are open to management via the existing `/api/admin/**` URL rule
    (OWNER/HR_ADMIN/MANAGER). **Mutations are `@PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")`**
    — MANAGER excluded, matching the work-location / leave-type config convention
    (managers approve requests; they don't author company config).
- **`CalendarService`** — gained `listForMonth` / `create` / `update` / `delete`
  (class stays `@Transactional(readOnly = true)`; writes are `@Transactional`).
  - Company-scoped: `create` sets `company` from the caller; `update`/`delete`
    resolve via `requireInCompany(...)`, which 404s when the row belongs to
    another tenant (mirrors `LeaveService`'s isolation check — never a 403, so a
    foreign id can't be probed).
  - `BIRTHDAY` is rejected with 400 on both create and update — birthdays are
    derived from employee records, not hand-authored.
  - PATCH is partial: null field = unchanged; an explicitly blank `timeLabel`
    clears it; a blank `title` is a 400 rather than a silent no-op.
  - **Class javadoc now records the payroll coupling:** HOLIDAY rows feed
    `CompensationService` (holiday pay + exclusion from unpaid-leave deductions),
    so editing them changes *future* runs. Re-running payroll is idempotent and
    never clobbers an APPROVED/PAID payslip, so approved periods are safe.
- **`dto/CalendarDtos`** — added `CompanyEventResponse`
  (`{ id, title, eventDate, eventType, accentColorKey, iconName, timeLabel }`,
  reusing `EventType.accentColorKey()` / `iconName()`),
  `CreateCompanyEventRequest` (`@NotBlank @Size(max=255) title`,
  `@NotNull eventDate`, `@NotNull eventType`, `@Size(max=64) timeLabel`) and
  `UpdateCompanyEventRequest` (all optional, same size limits).

## Tests
`calendar/CalendarAdminIT` (7 tests) — HR creates a HOLIDAY and it appears in
both the admin listing and the staff `GET /api/calendar` grid for that month;
PATCH moves it (the dot moves with it); DELETE 204s and it vanishes from both;
MANAGER can read but gets 403 on POST/PATCH/DELETE; EMPLOYEE is 403 on the whole
admin space; unauthenticated is 401 on all four; blank title / missing date /
missing type / `BIRTHDAY` → 400; a foreign company's admin gets 404 on
PATCH/DELETE and never sees the row.

**Fixture safety:** every case runs in a freshly-registered company and pins its
date to `YearMonth.now().plusMonths(6)`, so no payroll period under test ever
gains a HOLIDAY (which would move computed pay).

## Gate
`./scripts/test-backend.sh` → **BUILD SUCCESS**, 82 tests, 0 failures (was 75).
Dev docker stack untouched.

## Next
App side per the spec: `calendarAdminApi` + query hooks (invalidating both
`['adminCalendar']` and `['calendar']`), `app/admin/calendar-events.tsx` list and
`app/admin/calendar-event-edit.tsx` form, linked from the admin profile menu,
with the "public holidays are paid" helper note on the Holiday type.
