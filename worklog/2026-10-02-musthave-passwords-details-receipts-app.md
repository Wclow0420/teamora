# Must-have gaps (app half): passwords, My details, claim receipts

**Date:** 2026-10-02 · **Scope:** `app/` + `src/` only (backend half logged separately).
No new dependency, no `app.config.ts` / `package.json` change → ships as an OTA update.

## What changed

### Passwords
- **Change password** — shared `src/components/account/ChangePasswordScreen.tsx`, mounted by two
  thin routes (`app/(staff)/change-password.tsx`, `app/admin/change-password.tsx`) so "back"
  returns to whichever Profile tab opened it. Current / new / confirm, a "Show passwords" toggle,
  client validation (8–72 chars, new ≠ current, confirm matches), server `ApiError` inline
  ("Current password is incorrect" lands under that field). Success → Alert + back.
- `useChangePassword` sends this device's **refresh token** along with the body. The backend
  revokes the account's other refresh tokens and spares that one, so the user stays signed in
  here and is signed out elsewhere. (Extra optional field vs. the original spec — the backend
  added it; harmless if ignored.)
- **Admin reset password** — `app/admin/employee-reset-password.tsx`, opened from a "Reset
  password" button on the employee hub's **Employment** screen (not a sixth hub tile). Shown to
  OWNER/HR_ADMIN, hidden for the owner's record and for yourself (use Profile → Change password).
  The temporary password field is plain text on purpose — the admin must pass it on.
- **Login "Forgot password?"** was a dead `Text`; it's now a button that shows an honest Alert
  (ask HR to reset). No fake email flow.

### My details
- `app/(staff)/my-details.tsx` from a new "My details" row on the staff Profile. Read-only rows
  from `useMe()`; null values render "Not set"; no reporting manager renders "Company owner
  approves" (the real routing rule). Phone is the one editable field → `PATCH /api/employees/me`
  via `useUpdateMyDetails`, which invalidates `me`. Blank clears.

### Claim receipts
- `src/lib/receipt.ts` — back-camera capture at quality 0.4 (bare base64). Rear sensors are far
  larger than the selfie camera, so: we pick a moderate still size (long edge 1000–2000 px) from
  the camera's supported list, and if a shot is still over ~1.9 MB we retake once at 0.15 before
  giving up with a "too large" note — the app never sends a photo the 2 MB server cap would 400.
- `src/components/claims/ReceiptField.tsx` + `ReceiptCamera.tsx` — the "Add receipt photo" form
  field and its full-screen capture modal; thumbnail with Retake / Remove. Camera denied /
  unavailable / capture failure shows a note and **never blocks submit**.
- `src/components/media/AuthedImage.tsx` — the Bearer-header `<Image>` generalised out of
  `SelfieThumb` (which now composes it); `PhotoViewer.tsx` — full-screen viewer modal.
- `src/components/claims/ReceiptThumb.tsx` — real thumbnail when `hasReceipt` (tap → viewer),
  otherwise a neutral receipt `IconTile`. Replaces the striped `Placeholder label="rcpt"` in
  `claims.tsx`, and is shown on `ClaimApprovalCard` (with "No receipt attached" otherwise), so
  both the staff approvals inbox and the admin Approvals tab get it from the shared card.
- API layer: `Claim.hasReceipt`, `PendingClaim.hasReceipt`, `SubmitClaimBody.receiptBase64`,
  `claimApi.receiptUrl(id)`, `authApi.changePassword`, `employeeApi.updateMe` / `resetPassword`,
  hooks `useChangePassword` / `useResetEmployeePassword` / `useUpdateMyDetails`.
- Theme: added `onDark.control` and `onDark.scrim` (viewer/camera overlays) instead of inlining rgba.

## Non-obvious / honest notes
- **Not exercised end-to-end.** `npm run typecheck` is clean, but none of this was run against the
  new endpoints, and the camera capture can't be exercised in the iOS simulator (no camera) — the
  simulator path should land on the "couldn't take a photo" note. Needs a pass on a real device.
- `AuthedImage` uses the access token held at render time; if it has expired the image falls back
  (receipt tile / avatar) until any API call refreshes the token. Same limitation the selfie
  thumbnail already had.
- "My details" was added to the **staff** Profile only, as specified. Admins can already edit
  their own record from the employee hub; a self-service row on the admin Profile is a cheap
  follow-up (the screen only needs a second route file).
- `admin/live.tsx` still has its own inline selfie viewer; it could move to `PhotoViewer` later.
