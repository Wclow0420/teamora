# 2026-06-16 — Wire the app to the backend (real auth + live data)

## What changed

The React Native app now talks to the Spring Boot API instead of mock data.

### API layer (`src/api`)
- `config.ts` — base-URL resolver (env → derived dev LAN IP → localhost/10.0.2.2).
- `tokenStore.ts` — access/refresh JWTs in `expo-secure-store` (+ memory cache).
- `client.ts` — fetch wrapper: Bearer injection, `ApiError`, single-flight
  **401 → refresh → retry**, sign-out on refresh failure.
- `endpoints.ts` — typed functions per domain.
- `types.ts` — DTO mirrors; `accents.ts` — status/accent → theme colour.
- `queryClient.ts` + `queries.ts` — React Query hooks (queries + mutations,
  cache invalidation on mutate).

### Auth
- `AuthContext` rewritten: real `signIn(email, password)` → stores tokens, sets
  employee/role; `signOut` revokes the refresh token; **session restored on
  launch** via `/api/auth/me`. Status: `loading | authenticated | unauthenticated`.
- `app/index.tsx` shows a splash spinner while restoring, then routes by the
  server-provided role.
- `app/(auth)/login.tsx` does a real login with error handling. The secret
  logo-tap now just **pre-fills the admin demo account** (role is server-decided).
- Root layout wraps the app in `<QueryClientProvider>`.

### Screens wired to live data
- Staff: Home (today + me + live timer base from worked minutes; Clock Out
  mutation), Attendance (history/stats/chart/day-list), Leave (balances +
  requests), Claims (summary + list), Payslip (latest payslip), Profile (me +
  leave balance), Clock-in (clock-in mutation → back).
- Admin: Live (counts + roster, 30s refetch), Staff (directory), Approvals
  (pending leave + approve/reject mutations), Payroll (run summary), Dashboard
  (KPIs assembled from live/pending/summary).
- Added `<AsyncBoundary>` for per-section loading/error.

### Still mock (no endpoints yet)
Company Calendar, Notifications, admin Scheduling, and a few static Home tiles.

## Verification
- `npx tsc --noEmit` → clean.
- `npx expo export --platform ios` → bundles successfully.
- Backend up (`docker compose`), `POST /api/auth/login` → 200. Runtime
  app↔API depends on the device reaching the host: iOS sim uses localhost:8080;
  a physical device auto-uses the Mac's LAN IP via the Metro host; override with
  `EXPO_PUBLIC_API_URL`.

## Follow-ups
- Apply-leave / submit-claim forms (buttons are currently no-ops/TODO).
- Endpoints for calendar, notifications, scheduling, and a dashboard summary.
- Token-expiry edge cases on cold start; optimistic updates for approve/reject.
