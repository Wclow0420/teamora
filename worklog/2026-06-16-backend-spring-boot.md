# 2026-06-16 — Spring Boot + Postgres backend (foundation + core slice)

## What we built

A `backend/` module: Spring Boot 3.3 · Java 21 · PostgreSQL 16 · JWT auth with
RBAC. Package-by-feature under `com.teamora`.

### Foundation
- **Build/run:** `pom.xml` (Maven), multi-stage `Dockerfile` (builds with
  Maven+JDK21 in-container → slim JRE), `docker-compose.yml` (Postgres +
  pgAdmin + API). No local Java/Maven required.
- **Config:** `application.yml`, typed `TeamoraProperties` (`teamora.*`).
- **Security:** stateless JWT (`JwtService`, `JwtAuthenticationFilter`),
  `SecurityConfig` (BCrypt, CORS, `/api/admin/**` → ADMIN, rest authenticated),
  `CustomUserDetailsService`, `CurrentEmployeeService`.
- **Common:** `BaseEntity` (audit timestamps), `GlobalExceptionHandler` +
  `ApiError`, `ResourceNotFoundException` / `BadRequestException`.
- **Schema:** Flyway `V1__init.sql` defines ALL core tables; JPA runs in
  `ddl-auto=validate`.

### Domains (core vertical slice)
- **auth** — login / refresh (rotating, persisted refresh tokens) / logout / me.
- **employee** — entity + directory (admin) + `/me`; demo seeder (9 people).
- **attendance** — clock-in/out, today, monthly history + weekly hours, admin
  live board with status counts.
- **leave** — balances, my requests, apply; admin pending list + approve/reject
  (approval increments the balance).
- **claim** — my claims + pending total, submit; admin pending + approve/reject.
- **payroll** — my payslips + breakdown, single payslip, admin run summary.

Each domain seeds demo data (idempotent, ordered) matching the app's screens
(e.g. Amir's leave 12/8/3, claims pending RM 206.50, net pay RM 4,285.50).

## How we worked
Built the foundation + full schema + Employee/Auth by hand, then fanned the four
domains out to parallel agents against a strict contract (`ddl-auto=validate`
means entities must match the schema exactly). Verified by building the Docker
image and running the stack.

## Verification
- `docker build` → all Java compiles.
- `docker compose up` → Flyway migrates, **JPA schema validation passes**,
  seeders load (9 employees, 13 attendance, 3 balances + 6 leave, 4 claims, 9
  payslips), Tomcat starts in ~3s.
- API smoke test (curl): staff login → token; `/me`; attendance/leave/claims/
  payroll reads return app-matching data; admin login → live counts, pending
  approvals, payroll summary; **403** for staff on `/api/admin/**`; **401** on
  bad credentials.

## Notes / follow-ups
- DB host port is **5435** (other local Postgres instances occupy 5432–5434).
- Seeded payroll summary aggregates the ~9 seeded employees (real total, not the
  app's illustrative 612k headcount).
- **Not yet wired:** the React Native app still uses mock data — next step is an
  API client (token storage + refresh interceptor) replacing `src/data/mock.ts`
  and the prototype tap-to-unlock auth with real login.
- Production hardening TODO: real `TEAMORA_JWT_SECRET`, tighter CORS origins,
  rate limiting, refresh-token cleanup job, tests.
