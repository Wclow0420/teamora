# Must-have gaps (backend): passwords, self-service phone, claim receipts

**Date:** 2026-10-02 · **Scope:** `backend/**` only (app half is a separate change)

## What changed

### 1. Passwords
- `POST /api/auth/change-password` (authenticated) `{ currentPassword, newPassword, refreshToken? }` → 204.
  Wrong current → 400 "Current password is incorrect"; new password must be 8–72 chars
  (bean validation → 400 with `fieldErrors.newPassword`) and differ from the current one (400).
- `POST /api/employees/{id}/reset-password` (OWNER/HR_ADMIN) `{ newPassword }` → 204.
  Same-company only (cross-tenant → 404); HR_ADMIN resetting the OWNER → 403; 8–72 chars.
- **Sessions:** refresh tokens are DB rows, access tokens are stateless 30-min JWTs. Both
  endpoints bulk-revoke the target's active refresh tokens
  (`RefreshTokenRepository.revokeAllForEmployee`). Already-issued access tokens are *not*
  invalidated — they keep working until they expire (≤ 30 min), after which the device
  cannot refresh and must sign in again.
  - change-password: if the body carries the caller's own `refreshToken`, that one token is
    spared, so the device that changed the password stays signed in and only *other*
    devices are signed out. Without it, every session (including the caller's) is revoked.
    A token that isn't the caller's spares nothing.
  - reset-password: all of the employee's refresh tokens are revoked; the admin's are untouched.

### 2. My profile
- `PATCH /api/employees/me` `{ phone? }` → the same full detail as `GET /api/employees/me`.
  Phone is trimmed, blank clears it, > 32 chars → 400, absent → unchanged. The request
  record has only `phone`, so any other field in the body is dropped by Jackson.

### 3. Claim receipts
- **V17** `V17__claim_receipt_photo.sql`: `claims.receipt_photo BYTEA`, `receipt_photo_type VARCHAR(32)`.
- `SubmitClaimRequest.receiptBase64` (optional; bare base64 or data URL; 2 MB decoded cap → 400).
- `GET /api/claims/{id}/receipt` → image bytes. Allowed: the claim's owner, or whoever may
  decide it (HR_ADMIN/OWNER, or the MANAGER it routes to). Other same-company users → 403;
  other company → 404; no receipt → 404.
- `hasReceipt` on `ClaimResponse` (list, submit, approve/reject) and `PendingClaimResponse`.
- Decoding was extracted from `AttendanceService` into `common/PhotoCodec` and is now shared
  by the selfie and the receipt (same messages, same cap).

## Non-obvious
- **Receipt bytes are not on `Claim`.** `@Basic(LAZY)` does nothing without bytecode
  enhancement, so a `byte[]` on `Claim` would load every receipt whenever a claims list or
  approvals queue is fetched. `Claim` maps only `receipt_photo_type` (→ `hasReceipt`); a second
  narrow entity `ClaimReceipt` maps `id` + `receipt_photo` on the same table, written by a
  bulk update right after the insert and read only by the receipt endpoint. Column mapping is
  the same as the selfie (`byte[]` + `@JdbcTypeCode(VARBINARY)` + `bytea`).
  (Attendance still has the bytes on the entity — worth the same treatment later.)
- Cross-tenant receipt access is **404** (matching claim approve/reject), whereas the selfie
  endpoint answers 403 for the same case.
- `PhotoCodec` ignores a data-URL mime longer than 32 chars (falls back to `image/jpeg`)
  instead of overflowing the column.
- The legacy `claims.receipt_url` column/field is left in place, still unused.

## Tests
`auth/PasswordIT` (8), `employee/SelfProfileIT` (5), `claim/ClaimReceiptIT` (8).
`./scripts/test-backend.sh` → BUILD SUCCESS, 142 integration + 43 unit tests green.

## TODO / follow-ups
- App: send `refreshToken` with change-password so the current device stays signed in.
- CLAUDE.md §12 still lists migrations as V1–V10; now V1–V17.
- No forgot-password email flow (no mail infra) — admin reset is the recovery path.
