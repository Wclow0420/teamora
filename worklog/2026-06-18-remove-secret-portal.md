# 2026-06-18 — Remove the secret admin portal (single login, role decides)

## Why

With real authentication + server-issued roles + RBAC now in place, the
prototype's "tap the logo 5× to reveal admin" was no longer doing any security
work — it was vestigial. Security is enforced on the backend (admin APIs return
403 to non-managers), so hiding the admin entrance in the UI added nothing.
Decision: **one login; the server's role routes you.**

## What changed
- `app/(auth)/login.tsx`: removed the secret tap handler, the credential
  pre-fill, and the "Admin demo unlocked" card. The logo is now a plain mark.
  Footer shows both demo accounts (staff: amir@lumi.com, admin: sarah@lumi.com).
- `src/context/AuthContext.tsx`: dropped `SECRET_TAPS`, `registerSecretTap`,
  `adminHintUnlocked`, and the tap-count state. `AuthState` is now just
  `{ status, employee, role, signIn, signOut }`.
- Routing unchanged in behaviour: `isManagementRole(role)` → admin app, else
  staff app (`app/index.tsx` + login redirect).
- CLAUDE.md §7 retitled "Auth & role-based routing" and rewritten.

## Behaviour now
- Log in with any account → land in the experience that matches your role.
- amir@lumi.com → staff app; sarah@lumi.com → admin app. No hidden gestures.
- An EMPLOYEE cannot reach admin data even if they got to the screens (API 403).

## Verification
- No stale references to the secret-portal symbols.
- `npx tsc --noEmit` clean; `npx expo export` bundles.
