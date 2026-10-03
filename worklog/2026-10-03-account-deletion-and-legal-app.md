# Account deletion + privacy/terms — app side (round 5)

**Why:** App Store guideline 5.1.1(v) — an app that lets people create an account
must let them start deleting it in the app. Malaysian employers must keep
payroll/EPF/SOCSO/tax records, so an employee can't erase those themselves: the
company (data controller) decides. Backend endpoints landed in parallel.

## What changed
- **Delete company (OWNER):** Company settings → owner-only "Danger zone" row →
  `app/admin/delete-company.tsx`. Lists what's erased, warns to export payroll
  first (links to the Payroll tab, where each month's Reports & export lives),
  asks for the company name (case-insensitive, trimmed — same rule as the server)
  and the owner's password, "Delete everything" disabled until both are filled,
  final destructive Alert → `POST /api/companies/me/delete` → `signOut()` →
  onboarding. Server errors go under the field they're about.
  The admin Profile also shows a quiet "Delete company and account" link for the
  owner — an App Review account made via Register is an OWNER and will look on
  Profile first.
- **Request account deletion (non-owners):** shared
  `src/components/account/DeleteAccountScreen.tsx`, routed from
  `app/(staff)/delete-account.tsx` and `app/admin/delete-account.tsx`. Explains
  that HR removes the account and the employer keeps statutory records; optional
  reason (≤300) → `POST /api/employees/me/deletion-request` → success state with
  the server's `requestedAt`. Quiet "Delete my account" link under Log out on the
  staff Profile and (HR_ADMIN only) the admin Profile.
- **Deactivate / reactivate:** Employee → Employment gets "Deactivate account" /
  "Reactivate account" (OWNER/HR_ADMIN, not self, not the owner) with confirm
  Alerts, plus a "deactivated" banner. Hub's Employment summary leads with
  "Inactive".
- **Staff list:** `GET /api/employees` returns inactive people too. They stay
  listed — muted, "Inactive" chip, sorted last — because the list is the only way
  back to reactivate them or look up their history; the header counts active
  staff ("12 employees · 1 inactive"). Shift-assign's picker drops inactive people.
- **Privacy notice + terms:** text lives in `src/content/legal.ts` as structured
  sections (`LEGAL_DRAFT = true` shows "Draft — pending legal review"), rendered
  by `src/components/legal/LegalDocumentScreen.tsx` at `app/(auth)/privacy.tsx`
  and `terms.tsx` (reachable signed in or out). Login + Register footers link to
  both; both Profiles have a "Privacy & terms" row. `docs/legal/*.md` are generated
  with `node scripts/generate-legal-docs.js` — edit the .ts, rerun the script.
  Lawyer to-dos are in `LEGAL_DRAFTING_NOTES` (and at the foot of the md files):
  legal entity, BM translation, the placeholder `privacy@teamora.app` mailbox,
  hosting region, backup retention, PDPA 2024 amendments.
- **New UI kit piece:** `PointList` (icon-led explanatory points in a card).
- **AuthContext.signOut is now single-flight.** For an account that no longer
  exists server-side, the push-token removal 401s → failed refresh → the
  unauthorized handler → signOut again, which looped. Nested calls now join the
  sign-out already in progress.

## Not verified
- Typecheck only. Not run on a device/simulator in this pass.
