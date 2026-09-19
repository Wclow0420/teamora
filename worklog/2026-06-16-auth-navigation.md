# 2026-06-16 — Fix sign-in navigation + wire logout

## Problem

Tapping **"Sign in"** or **"Enter admin dashboard"** did nothing. The buttons
called `signInStaff()` / `signInAdmin()`, which set `role` in `AuthContext`, but
the only role→route redirect lived in `app/index.tsx`. Since the user was on
`/login` (not `/index`), changing the role didn't move them anywhere.

## Fix

- `app/(auth)/login.tsx`: after setting the role, navigate explicitly —
  `router.replace('/home')` for staff and `router.replace('/admin/dashboard')`
  for admin.

## Also wired logout (so roles can be switched without a reload)

- `app/(staff)/(tabs)/profile.tsx`: the "Log out" row now calls `signOut()` and
  `router.replace('/onboarding')`.
- `app/admin/dashboard.tsx`: the header avatar is now a `Pressable` that signs
  out and returns to onboarding.

`signOut()` already existed in `AuthContext` (it also resets the secret-tap
state), so this was just wiring UI to it.

## Verification

- `npx tsc --noEmit` → clean.

## Follow-up (not blocking)

The cleaner long-term pattern is auth-gated group layouts (a `useEffect`
redirect in `(staff)`/`admin` `_layout` that bounces to `/onboarding` when
`role` is null, and in `(auth)` that bounces to the app when `role` is set).
Explicit `router.replace` is fine for the prototype; revisit when real auth
lands.
