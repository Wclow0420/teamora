# Deploying the Teamora API

Teamora stores NRIC numbers, salaries, bank accounts, tax data and selfies. Treat
every step below as required, not optional polish.

The production stack is `docker-compose.prod.yml`: the API and Postgres only. There is
no pgAdmin, the database publishes no ports, and the API listens on `127.0.0.1:8080`
behind a TLS reverse proxy on the same host. `TEAMORA_ENV=production` is fixed there.
With it, the API **refuses to start** if any of these is true:

- `TEAMORA_SEED=true` (the demo accounts share a public password);
- `PASSWORD_RESET_EXPOSE_CODE=true`;
- the JWT secret is missing, shorter than 32 bytes, or a published placeholder;
- password reset is enabled but the only sender writes codes to the log.

Production also turns off the Swagger UI and `/v3/api-docs`.

```bash
cd backend
cp .env.example .env.prod      # then edit — see the table below
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f api
```

Flyway migrations run automatically on startup. Back up before upgrading (see below).

## Environment variables

| Variable | Required | Default | Notes |
| --- | --- | --- | --- |
| `POSTGRES_PASSWORD` | **yes** | – | Long random value. Compose refuses to start without it. |
| `TEAMORA_JWT_SECRET` | **yes** | – | ≥ 32 bytes, e.g. `openssl rand -base64 48`. Rotating it signs everyone out. |
| `POSTGRES_DB` / `POSTGRES_USER` | no | `teamora` | |
| `TEAMORA_CORS_ORIGINS` | no | `*` | `*` allows any origin **without** credentials. A comma-separated list allows credentials. The mobile app needs no CORS. |
| `PASSWORD_RESET_ENABLED` | no | `false` (prod) | Keep it `false` until a real email/SMS sender exists. While it's off, "Forgot password" answers 200 but sends nothing, and admins reset passwords from the Staff screen instead. |
| `API_PORT` | no | `8080` | Host loopback port the reverse proxy forwards to. |
| `TEAMORA_ENV` | fixed | `production` | Set by the prod compose file. Local dev uses `dev`. |
| `TEAMORA_SEED` / `PASSWORD_RESET_EXPOSE_CODE` | fixed | `false` | Set by the prod compose file. Startup fails if either is true. |

Health check: `GET /actuator/health` returns `{"status":"UP"}`. It includes the
database check and shows no details. Every other actuator endpoint is off.

## TLS reverse proxy

Never expose port 8080 to the internet. Put Caddy (automatic HTTPS) or nginx in front
of it on the same host. The API trusts `X-Forwarded-*` only from loopback and
private-range proxies, so client IPs (used by rate limiting) and the scheme are correct.

`/etc/caddy/Caddyfile`:

```caddy
api.example.com {
    encode gzip
    request_body {
        max_size 4MB        # the API itself rejects bodies over 3.5 MB with 413
    }
    reverse_proxy 127.0.0.1:8080
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options nosniff
        -Server
    }
}
```

Point the app's `EXPO_PUBLIC_API_URL` at `https://api.example.com`.

## Backups

**Prefer a managed Postgres with point-in-time recovery** (e.g. AWS RDS, DigitalOcean,
Neon, Supabase), with automated daily snapshots and at least 7 days of PITR. Point
`SPRING_DATASOURCE_URL` at it and drop the `db` service.

If you self-host the `db` container, take a nightly logical dump and copy it **off the
server** (object storage with versioning, or another region). An example crontab on
the host:

```cron
# 02:30 every night: compressed custom-format dump, keep 14 days locally
30 2 * * * cd /opt/teamora/backend && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T db \
  pg_dump -U teamora -d teamora -Fc > /var/backups/teamora/teamora-$(date +\%F).dump \
  && find /var/backups/teamora -name '*.dump' -mtime +14 -delete
```

Then sync `/var/backups/teamora` off-host (e.g. `rclone sync` or `aws s3 sync`).
Encrypt the dumps at rest, because they contain everything above.

### Restore test (do this before go-live, then monthly)

A backup you've never restored is a hope, not a backup.

```bash
# 1. Start a throwaway Postgres
docker run -d --name teamora-restore -e POSTGRES_PASSWORD=x postgres:16-alpine
sleep 5
# 2. Restore the latest dump into it
docker exec -i teamora-restore createdb -U postgres teamora
docker exec -i teamora-restore pg_restore -U postgres -d teamora --no-owner < /var/backups/teamora/teamora-YYYY-MM-DD.dump
# 3. Sanity-check row counts against production
docker exec -i teamora-restore psql -U postgres -d teamora -c \
  "select (select count(*) from companies) companies, (select count(*) from employees) employees, (select max(period) from payslips) last_payroll;"
# 4. Clean up
docker rm -f teamora-restore
```

Write down how long the restore took and who checked it.

## Logs and incidents

- Every response carries an `X-Request-Id` header, and every log line carries the same
  id. An unexpected error tells the user `Something went wrong (ref ABC123)`. Search the
  logs for `ref=ABC123` to find the full stack trace.
- Admin actions on pay and access go to the `audit_events` table:
  - bank, salary and role changes;
  - admin password resets;
  - deactivate and reactivate;
  - payroll approve and mark-paid.

  Read it with `GET /api/admin/audit?employeeId=…` (OWNER/HR_ADMIN).
- Rate limits are in-memory, per API instance:
  - login: 10 per 15 min per email, and 30 per 15 min per IP;
  - register and forgot-password: 5 per minute per IP;
  - reset-password: 10 per 15 min per IP.

  Restarting the API resets them.
