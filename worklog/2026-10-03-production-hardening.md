# 2026-10-03 — Production hardening (security audit follow-up)

A read-only audit (see conversation) found 5 blockers and several high items before any real
company data goes in. This round fixes everything that doesn't need an owner decision.

## Done
- **Startup safety:** no JWT default anywhere; JwtService fails fast on a missing/short secret
  (and placeholders in production). Seeding defaults off and every seeder needs
  `teamora.seed=true`. `TEAMORA_ENV=production` refuses unsafe config (seed on, exposed reset
  code, reset enabled with only the log sender). Swagger off in production.
- **Deploy kit:** `docker-compose.prod.yml` (no published DB port, no pgAdmin, API on
  127.0.0.1, healthcheck), `backend/DEPLOY.md` (env table, Caddy TLS + 4 MB body limit,
  nightly pg_dump, restore test), non-root Dockerfile, actuator `/actuator/health`.
- **Errors:** generic "Something went wrong (ref XXXXXX)" with the full error logged under a
  request id; proper 404/405/409/413/415/429; `include-message: never`; size limits on every
  free-text request field.
- **Abuse:** in-memory rate limits on login/register/forgot/reset; 10 reset codes per account
  per day; 3.5 MB request cap; photos must really be JPEG/PNG/HEIC (magic bytes).
- **Data:** managers no longer receive NRIC/salary/bank fields (list is slim; detail nulls
  them for MANAGER; payroll summary + dashboard payroll figure are OWNER/HR only).
  Selfie bytes moved to `attendance_photos` (V23) so lists never load them; managers see
  only their reports' selfies. Refresh tokens stored hashed (V24) with reuse detection.
  `audit_events` (V25) + bank-change notification to the employee. Lower-case unique
  email (V26). CSV formula injection guard. Push: timeouts, own-token-only removal.
- **App:** `EmployeeSummary` type for the list, payroll-due tile tolerates null, admin
  layout redirects non-admins (deep links), offline/419 untouched.
- **Account lock-out** (earlier today): inactive users rejected by JWT filter, refresh, login.

## Verified
Backend gate: 290 tests green (60 unit + 230 IT). Migrations V23–V26 rehearsed on a copy of
the dev DB, then applied to dev. Simulator: HR login, dashboard, Live attendance.

## Note
Commit 03de12d accidentally included an early snapshot of ~30 backend files from this round
(I ran `git add -A` while the agent was still working); the follow-up commit completes it.

## Still needs owner decisions (docs/LAUNCH_DECISIONS.md)
Hosting + backups, email provider (then the real `PasswordResetSender`), pilot company,
lawyer review of docs/legal. Column encryption deferred.
