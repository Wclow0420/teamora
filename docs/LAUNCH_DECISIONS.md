# Teamora — launch decisions (owner input needed)

_Prepared 3 Oct 2026. Prices are list prices found online that day — confirm on each provider's pricing page before signing up._

Three decisions block a real pilot. Everything else (security hardening, account deletion, privacy notice draft) is being done.

---

## 1. Where the backend runs

Requirements: Java 21 container, PostgreSQL 16, HTTPS, daily backups with point-in-time recovery, data close to Malaysia
(PDPA prefers it; latency matters for clock-in).

| Option | What it is | Rough monthly cost (pilot size) | Backups | Effort |
|---|---|---|---|---|
| **A. DigitalOcean (Singapore)** — App Platform for the API + Managed PostgreSQL | Managed containers + managed DB | API from ~US$5–12, DB from ~US$15 → **~US$20–30** | Managed daily backups + PITR on the DB | Low — push the Docker image, set env vars |
| **B. AWS Lightsail (Malaysia, ap-southeast-5)** — container service + Lightsail managed DB | Simple AWS, **data stays in Malaysia** | Similar order to A (check Lightsail pricing page) | Automatic daily snapshots | Low–medium |
| **C. Single VPS + `docker-compose.prod.yml`** (any provider) | One server running API + Postgres + Caddy for HTTPS | ~US$6–12 | **We** run nightly `pg_dump` off-host (script in `backend/DEPLOY.md`) | Medium, and we own patching/backups |

**Recommendation: A for the pilot** (cheapest managed option with proper backups, least to look after). Choose **B** if a
customer requires their data to stay in Malaysia. Avoid C for real payroll data unless cost is critical — backups and
security patches become our job.

## 2. Email provider (turns on "Forgot password" for real users)

| Provider | Free tier | Paid | Notes |
|---|---|---|---|
| **Resend** | 3,000/month (100/day) | US$20/mo for 50k | Simplest API; good default |
| **Amazon SES** | AWS credits for new accounts | US$0.10 per 1,000 | Cheapest at scale; more setup (domain verification, sandbox exit) |
| **Postmark** | 100/month | ~US$15/mo for 10k | Best deliverability reputation |

**Recommendation: Resend** — the pilot will send a handful of emails a day, which fits the free tier, and it is a
small code change (one `PasswordResetSender` class). Needs: a domain you own (e.g. teamora.app) with DNS access to add the
verification records.

## 3. A pilot company

One real company, 5–10 staff, for one full payroll cycle (clock-ins, leave, claims, one payroll run, statutory export).
Ideally one where you can sit with the HR person for the first payroll.

---

## After you decide (what I do)

1. Deploy with `docker-compose.prod.yml` / the chosen platform, `TEAMORA_ENV=production`, real secrets, seeding off.
2. Point a domain at it with HTTPS; set the production `EXPO_PUBLIC_API_URL` in `eas.json`.
3. Wire the email sender; turn forgot-password on.
4. Test a backup restore once.
5. Ask you to approve one production build (iOS; Android if you want it).
6. Before App Store submission: host `docs/legal/privacy.md` + `terms.md` at a public URL (App Store Connect requires a
   privacy policy URL) — after a lawyer reviews the draft.
