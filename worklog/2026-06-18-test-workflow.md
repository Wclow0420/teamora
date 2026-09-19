# 2026-06-18 — Test suite + standard "definition of done" workflow

## Why

Established the missing automated-test layer and a single health-check command,
so every new endpoint gets a test and we can catch problems before shipping.

## What we added

### Backend tests (`backend/src/test/java`)
- `AbstractIntegrationTest` — `@SpringBootTest` + `@AutoConfigureMockMvc`; boots
  the full app, security chain, Flyway, and seeders against a real Postgres
  (datasource from `SPRING_DATASOURCE_*`). Provides a `login()` helper.
- `auth/AuthAndTenantIT` (6 tests) — login returns role+company, 401 on bad
  creds, EMPLOYEE→admin endpoint = 403, unauthenticated = 401, **signup creates
  an isolated tenant** (can't see other companies' staff), duplicate-email
  rejected.
- `leave/LeaveFlowIT` (3 tests) — apply leave → PENDING, admin sees pending +
  approves, EMPLOYEE can't approve (403).
- `maven-failsafe-plugin` wired so `*IT` run during `mvn verify`.

### Runner — `backend/scripts/test-backend.sh`
Runs `mvn verify` in a Maven container on the Compose network against a
disposable `teamora_test` database (dropped/recreated each run, dev data
untouched). No local Java/Maven needed — just Docker. A cached `teamora-m2`
volume speeds re-runs.

> Note: started with Testcontainers (the usual best practice) but Docker Desktop
> on macOS denies mounting the engine socket into a container, and this machine
> has no host JDK — so the disposable-DB-on-the-Compose-network approach is the
> pragmatic, actually-runnable choice here. CI (Linux) can use either.

### Bug fixed while writing tests
Unauthenticated requests returned **403**; correct REST semantics is **401**.
Added an `authenticationEntryPoint` in `SecurityConfig` → unauthenticated = 401,
authenticated-but-forbidden = 403. Rebuilt the dev container; verified live.

## Standard workflow (now in CLAUDE.md §13)
- Every new endpoint → an `*IT` test (happy path + 401/403 + validation).
- Before done, run: `npm run typecheck` (app) and `cd backend && ./scripts/test-backend.sh` (backend).

## Verification
`./scripts/test-backend.sh` → **9 tests, BUILD SUCCESS**. App `tsc` clean. Live
API: unauth = 401, bad login = 401, good login = 200.
