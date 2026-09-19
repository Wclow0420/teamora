# Teamora API (backend)

Spring Boot 3.3 · Java 21 · PostgreSQL 16 · JWT auth (RBAC). The HR & Payroll API
behind the Teamora mobile app.

## Configuration (`.env`)

All config lives in `backend/.env` (gitignored). Copy the template and tweak:

```bash
cd backend
cp .env.example .env             # then edit values (esp. TEAMORA_JWT_SECRET for prod)
```

`docker-compose.yml` reads `.env` automatically (with built-in defaults, so it
runs even without one). Keys: `POSTGRES_*`, `DB_HOST_PORT` (default 5435),
`API_PORT` (8080), `TEAMORA_JWT_SECRET`, `TEAMORA_SEED`, `TEAMORA_CORS_ORIGINS`,
`PGADMIN_*`. For a non-Docker run, `application.yml` reads the same variable
names from the environment.

## Run it (Docker — no local Java/Maven needed)

```bash
cd backend
docker compose up --build        # starts Postgres + the API (+ pgAdmin)
```

- API → http://localhost:8080
- Swagger UI → http://localhost:8080/swagger-ui.html
- pgAdmin → http://localhost:5050  (admin@teamora.local / admin)
- Postgres → localhost:**5435** (host) → container `db:5432` (user/pass/db: `teamora`)

> The DB host port is **5435** to avoid clashing with other local Postgres
> instances. Change it in `docker-compose.yml` if you like — the API always
> talks to the DB over the internal Docker network, so it's unaffected.

Stop: `docker compose down` (add `-v` to wipe the database volume and re-seed).

### Run without Docker
Needs JDK 21 + a Postgres on `localhost:5432` (db `teamora`). Then
`./mvnw spring-boot:run` (or `mvn spring-boot:run`). Connection + secret are
overridable via `SPRING_DATASOURCE_*` and `TEAMORA_JWT_SECRET` env vars.

## Multi-tenancy & roles

The **company** is the tenant; data is isolated by `company_id` on every
tenant-scoped table. **Roles:** `OWNER`, `HR_ADMIN`, `MANAGER`, `EMPLOYEE`.
Management roles (everything except EMPLOYEE) get `/api/admin/**`; OWNER/HR_ADMIN
can add employees and change roles. New companies self-register via
`POST /api/auth/register` (creates the company + its OWNER).

## Demo accounts (seeded automatically; password = `password`)

| Email                  | Role     | Company        | Notes |
| ---------------------- | -------- | -------------- | ----- |
| `amir@lumi.com`        | EMPLOYEE | Lumi Foods     | full demo data (attendance, leave, claims, payslip) |
| `sarah@lumi.com`       | HR_ADMIN | Lumi Foods     | the admin experience |
| `owner@lumi.com`       | OWNER    | Lumi Foods     | |
| `nadia@lumi.com`       | MANAGER  | Lumi Foods     | |
| `admin@nusantara.com`  | OWNER    | Nusantara Tech | separate tenant — proves isolation |

Seeding is idempotent — it only runs on an empty database (`TEAMORA_SEED=true`).

## Auth flow

1. `POST /api/auth/login` `{ email, password }` → `{ accessToken, refreshToken, expiresIn, employee }`
2. Send `Authorization: Bearer <accessToken>` on every request.
3. `POST /api/auth/refresh` `{ refreshToken }` → new pair (old refresh token is rotated/revoked).
4. `POST /api/auth/logout` `{ refreshToken }` → revokes it.

Access token: HS256, 30-min TTL. Refresh token: persisted, 14-day TTL.
`/api/admin/**` requires the `ADMIN` role; everything else requires a valid token.

## Endpoints (core slice)

**Auth** — `POST /api/auth/register` (company + owner) · `POST /api/auth/login` · `POST /api/auth/refresh` · `POST /api/auth/logout` · `GET /api/auth/me`

**Company** — `GET /api/companies/me` · `PATCH /api/companies/me` (OWNER/HR_ADMIN)

**Employees** — `GET /api/employees/me` · `GET /api/employees?dept=&q=` (mgmt) · `GET /api/employees/{id}` (mgmt) · `POST /api/employees` (OWNER/HR_ADMIN — add) · `PATCH /api/employees/{id}/role` (OWNER/HR_ADMIN)

**Attendance** — `POST /api/attendance/clock-in` · `POST /api/attendance/clock-out` · `GET /api/attendance/today` · `GET /api/attendance/me?month=YYYY-MM` · `GET /api/admin/attendance/live` (admin)

**Leave** — `GET /api/leave/balances` · `GET /api/leave/requests` · `POST /api/leave/requests` · `GET /api/admin/leave/requests?status=PENDING` (admin) · `POST /api/admin/leave/requests/{id}/approve|reject` (admin)

**Claims** — `GET /api/claims` · `POST /api/claims` · `GET /api/admin/claims?status=PENDING` (admin) · `POST /api/admin/claims/{id}/approve|reject` (admin)

**Overtime** — `GET /api/overtime` · `POST /api/overtime` · `GET /api/admin/overtime` (routed) · `POST /api/admin/overtime/{id}/approve|reject`

**Payroll** — `GET /api/payroll/payslips` · `GET /api/payroll/payslips/{period}` · `GET /api/admin/payroll/summary?period=YYYY-MM` (admin) · **run engine** (OWNER/HR_ADMIN): `GET /api/admin/payroll/run?period=YYYY-MM` (current run state) · `POST /api/admin/payroll/run` `{period}` (generate/refresh DRAFT payslips from salary + approved OT + approved claims, real EPF/SOCSO/EIS via `PayrollCalculator`) · `POST /api/admin/payroll/run/{period}/approve` (DRAFT→APPROVED) · `POST /api/admin/payroll/run/{period}/mark-paid` (APPROVED→PAID)

**Calendar** — `GET /api/calendar?month=YYYY-MM` (company events + your approved leave as month dots + upcoming)

**Notifications** — `GET /api/notifications` (Today/Earlier + unread count) · `POST /api/notifications/read-all` · `POST /api/notifications/push-token` · `DELETE /api/notifications/push-token?token=` — auto-created on approve/reject (notifies the requester) and on submit (notifies the approver); each also fans out as an Expo **push** to the employee's registered devices

**Scheduling** — `GET /api/admin/schedule?weekStart=YYYY-MM-DD` (week roster) · `POST /api/admin/schedule` (assign a shift) — admin

**Dashboard** — `GET /api/admin/dashboard` (admin) — aggregated KPIs (headcount, present / on-leave today, role-routed pending-approval count, payroll due), the current-week attendance bar chart, and a recent-activity feed merged from leave/claim/overtime. Read-only; reuses the per-feature services.

> **Approvals routing:** the `/api/admin/{leave,claims,overtime}` queue +
> approve/reject endpoints are role-aware — a MANAGER sees/decides only their
> direct reports' requests; HR_ADMIN/OWNER see & decide anything.

## Architecture

- **Package-by-feature** under `com.teamora`: `auth`, `company`, `employee`,
  `attendance`, `leave`, `claim`, `overtime`, `payroll`, `calendar`, `schedule`,
  `notification`, `dashboard`, plus `config`, `security`, `common`, `seed`.
- **Flyway** owns the schema (`db/migration/V1__init.sql`); JPA runs in
  `ddl-auto=validate` so entities must match the schema exactly.
- **Spring Security** stateless JWT filter chain; passwords BCrypt-hashed.
- Uniform error body via `GlobalExceptionHandler` (`ApiError`).
- Audit timestamps via `BaseEntity` (`@EnableJpaAuditing`).

## Verified

`docker compose up` boots clean: Flyway migrates, JPA validation passes, seeders
load demo data, and the full auth + staff + admin + RBAC flow works (login,
`/me`, attendance/leave/claims/payroll reads, admin live/approvals/summary,
403 for staff on admin routes, 401 on bad credentials).
