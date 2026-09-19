# 2026-06-24 — Empty-state + honesty polish sweep

## Why
A cross-cutting quality pass: every list/section should have a friendly **empty
state** (loaded-but-no-data) instead of a blank gap, and a few screens still had
**hardcoded fake stat strings** that slipped past earlier passes — direct
violations of "never show fake data" (CLAUDE.md §0).

## Shared component
- **`src/components/ui/EmptyState.tsx`** (+ barrel export) — centered tinted icon +
  title + optional subtitle + optional CTA button. Used inside an `AsyncBoundary`
  success branch when a collection is empty. (Loading/error stay with AsyncBoundary.)

## Empty states added
- Staff: **payroll** (no payslips — was a blank screen), **leave** (no requests +
  "Apply for leave" CTA), **claims** (no claims + "Add a claim" CTA),
  **notifications** (upgraded the plain-text placeholder to the component),
  **attendance** (no history this month). Calendar already had honest empties — left.
- Admin: **staff** (no-results-for-search vs. no-staff-yet, with an "Add employee"
  CTA), **live** (nobody clocked in yet), **schedule** (no shifts for the day +
  "Assign shift" CTA, replacing an inline card). Approvals already used
  `EmptyApprovals` on every tab — left.

## Fake strings removed (honesty)
- **admin/staff** — the biggest offender: a hardcoded header subtitle
  **"156 employees · 8 teams"** and a fake department chip row (`All·156`,
  `Retail·48`, `Tech·36`, `Marketing·28`). Removed the chip row (no real
  per-dept counts client-side); subtitle now `` `${count} employees` `` from
  `useStaff().data.length`. Also turned the **decorative search box** (a static
  `Text`) into a real `TextInput` wired to the existing `useStaff(q)` server-side
  search + a clear button.
- **staff/leave** — header subtitle **"2 days pending approval"** → real
  `pendingCount` from `requests.data` (`"N pending approval"` / "Up to date").
- **staff/attendance** — accessory chip **"June 2026"** (static literal) → derived
  from the real `q.data.month` via a `formatMonth` helper.

## Verification
- `npm run typecheck` clean (integrated, after both work-streams).
- Final grep sweep for month-name / "N <noun>" literals in JSX → only code-comment
  matches remain (docstrings); no on-screen fake stats left.

## How it was done
Built the shared component, then fanned out the application across two agents on
disjoint file sets (staff screens / admin screens), each handling both empty states
and fake-string removal; reviewed both. The admin/staff fake-data removal was the
standout find.

## Follow-ups
- admin/live still shows a decorative office-floor `Placeholder` map with static
  pins — clearly placeholder imagery (not a presented stat), left in scope; revisit
  if/when real location data exists.
