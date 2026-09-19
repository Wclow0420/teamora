# Teamora — Engineering & Design Guide (CLAUDE.md)

Teamora is a Malaysia-focused **HR & Payroll** mobile app built with **React
Native + Expo (Expo Router) + TypeScript**, shipped via **EAS**. One binary
hosts **two experiences** — the **Staff** app (employees) and the **Admin** app
(managers/HR) — in a single, consistent **"Warm & Human"** design language.

This document is the source of truth for *how we build*. Read it before adding
screens, components, or features. Keep it current.

---

## 0. My role & operating mandate

The owner has delegated this app to me (Claude) to run as **full-stack engineer,
project manager, and product owner** in one. Operating principles:

- **Own the outcome, not the ticket.** Take each request to its genuinely-done
  state — backend + app + tests + docs + worklog — without waiting to be walked
  through every step. Drive the work end to end; only stop to ask when there's a
  real product trade-off (cost, scope, irreversible action), not for routine
  decisions a competent owner would just make.
- **User first, always.** Judge every change by whether it makes the app
  clearer, faster, kinder, and more trustworthy for a real employee or manager.
  Concretely: **never show fake or placeholder data** — if a number/stat is on
  screen it must be real; prefer fewer taps and plain Malaysian-workplace
  language; design forgiving forms and honest empty / loading / error states;
  respect the "Warm & Human" feel. When two paths are equal in effort, pick the
  one a non-technical user would thank me for.
- **Protect the codebase.** Uphold the golden rules (§1), the test gate (§13),
  `ddl-auto=validate` migration discipline (§12), and the design system. No
  shortcuts that a teammate would have to clean up later.
- **Manage the roadmap.** Keep a running sense of what's most valuable next.
  When a task finishes, verify it, log it, and propose the next highest-value
  step rather than going idle — but don't sprawl into unrequested rewrites.

---

## 1. Golden rules

1. **Never hard-code a value that belongs to the theme.** No raw hex, no font
   names, no magic radii in components. Pull from `@/theme`. If a token is
   missing, add it to the theme — don't inline it.
2. **Compose from the UI kit.** Before writing a `View`+`Text` block, check
   `@/components/ui`. New visual patterns that repeat become components.
3. **Screens are thin.** A screen composes components and wires data/navigation.
   Business logic lives in hooks; shared visuals live in components.
4. **One job per file.** A component file exports one component (+ its local
   types). Barrels (`index.ts`) are for re-export only.
5. **Type everything.** `strict` is on. Avoid `any`; the only sanctioned use is
   the Expo Router `tabBarIconName` option cast (documented below).
6. **Match the design.** We port the approved "Warm & Human" prototype
   pixel-faithfully. When in doubt, open the source design and mirror it.
7. **Log your work.** Every meaningful change gets an entry in `/worklog`
   (see §9).

---

## 2. Tech stack

| Concern            | Choice                                                  |
| ------------------ | ------------------------------------------------------- |
| Framework          | Expo SDK 54 (React Native 0.81, React 19, New Arch)     |
| Language           | TypeScript (strict)                                     |
| Navigation         | Expo Router 6 (file-based, typed routes)                |
| Build/Release      | EAS Build + EAS Update (`eas.json`)                     |
| Fonts              | `@expo-google-fonts/figtree`                            |
| Vector icons       | `react-native-svg` (custom 24px stroke set)             |
| Gradients          | `expo-linear-gradient`                                  |
| Animation          | `react-native-reanimated` 4 (+ `react-native-worklets`) |
| Haptics            | `expo-haptics`                                          |
| Safe areas         | `react-native-safe-area-context`                        |
| Push notifications | `expo-notifications` + `expo-device` (Expo push; §8)    |
| Documents / share  | `expo-print` + `expo-sharing` (client-side payslip PDF) |
| Server state       | `@tanstack/react-query` (see §8)                        |

> **Reanimated 4 note:** the Babel plugin now ships from
> `react-native-worklets/plugin` (listed last in `babel.config.js`), and
> `react-native-worklets` is a direct dependency. `babel-preset-expo` is a
> direct devDependency (no longer hoisted in SDK 54).

Path alias: **`@/*` → `./src/*`** (see `tsconfig.json`). Routes live in `app/`.

---

## 3. Project structure

```
app/                         # Expo Router routes (file-based)
  _layout.tsx                # Root: fonts, splash, providers, root Stack
  index.tsx                  # Auth gate → redirects by role
  (auth)/                    # Signed-out flow (group, no URL segment)
    onboarding.tsx
    login.tsx register.tsx   # one login; role decides the app (§7)
  (staff)/                   # Employee app (group)
    _layout.tsx              # Stack: tabs + pushed detail screens
    (tabs)/                  # Pill bottom-nav tabs
      _layout.tsx
      home.tsx attendance.tsx calendar.tsx payroll.tsx profile.tsx
    # pushed screens (lists + their submit forms, + manager inbox):
    leave.tsx leave-apply.tsx claims.tsx claim-submit.tsx
    clock-in.tsx overtime-submit.tsx approvals.tsx notifications.tsx
  admin/                     # Admin app (path segment → /admin/*)
    _layout.tsx              # Stack: tabs + pushed detail screens
    (tabs)/                  # Pill bottom-nav tabs
      _layout.tsx
      dashboard.tsx staff.tsx approvals.tsx payroll.tsx profile.tsx
    # pushed screens:
    live.tsx schedule.tsx shift-assign.tsx
    employee-new.tsx employee-edit.tsx company-settings.tsx

src/
  theme/                     # Design tokens — the single styling source
    colors.ts typography.ts spacing.ts shadows.ts index.ts
  api/                       # Live API client + React Query hooks (§8)
  components/
    ui/                      # Reusable presentational kit (+ index barrel)
    layout/                  # Screen frame, AsyncBoundary
    navigation/              # PillTabBar (custom tab bar)
    approvals/               # Approval cards (leave/claim/overtime)
  context/                   # React contexts (AuthContext)
  hooks/                     # Reusable hooks (useLiveTimer, useAuth re-export)
  lib/                       # Framework-agnostic helpers (payslip PDF: expo-print)
  notifications/             # Push registration + foreground listeners (§8)

worklog/                     # Dated change log (§9)
```

**Why `(staff)` is a group but `admin` is a path segment:** staff is the primary
experience and earns clean URLs (`/home`, `/payroll`). Admin is namespaced
under `/admin/*` to (a) avoid route collisions (both have a `payroll` screen)
and (b) keep the manager surface clearly separated.

---

## 4. Theme system (`src/theme`)

Import once: `import { theme } from '@/theme'` — or named tokens directly
(`import { palette, font, radius, gradients, tint, shadows } from '@/theme'`).

- **`colors.ts`** — `palette` (surfaces, ink scale, accents, line), `tint`
  (soft accent backgrounds), `gradients` (LinearGradient pairs), and the
  `accents`/`accentTint` maps for data-driven status colours.
- **`typography.ts`** — Figtree weights mapped to font-family names. Use
  `font(700)` for a `{ fontFamily }` fragment, or the `type` presets. **Never**
  set `fontFamily` by hand and **never** use `lineHeight: 1` (RN needs px).
- **`spacing.ts`** — `spacing`, `radius`, `touchTarget`. Default screen padding
  is `spacing.screenX` (20).
- **`shadows.ts`** — warm brown-tinted elevation (`card`, `float`, `coral`).
  Spread into a style array: `style={[base, shadows.card]}`.

The "Warm & Human" palette in one glance: cream `#FBF5EE` bg, white surfaces,
ink `#2C2620`, accents **coral** `#EC6A4D` / **amber** `#F2A93B` / **sage**
`#5C9070` / **violet** `#8268B5`, hairline `#EAE0D4`. Light mode only (for now).

### Elevated design language (the anti-"AI-slop" rules)
Ported prototype → **top-1% execution**. Hold to these:
- **Flat, confident colour over gradients.** Gradients are the generic-UI tell —
  use them *only* on a genuine hero surface, and keep them to a single-hue deepen
  (see `gradients`), never a wide multi-hue ramp. CTAs/cards/avatars are solid.
- **Espresso heroes.** Headline numbers (net pay, worked time, balances) live on
  the dark `palette.espresso` `HeroCard` with one restrained coral glow — not a
  cream gradient card.
- **Type leading is sacred.** Heading leading ≈ 1.2–1.3× size, body ≈ 1.5× —
  never ≈ 1.0 (that clips descenders; it was a real bug). Negative tracking on
  large text; positive tracking only on tiny all-caps eyebrows. Prefer the `type`
  presets over inline `fontSize`.
- **Tabular numerals** on every money/number column: `fontVariant: ['tabular-nums']`.
- **Warm, layered elevation.** Soft low-opacity brown-tinted ambient shadows
  (`shadows.card`/`float`), not hard grey drops or neon glows.
- **Subtotals sit in a sunken footer row** (`palette.surfaceSunken`); secondary
  rows (e.g. statutory deductions) go tile-less + secondary weight/colour.

---

## 5. Component kit (`src/components`)

Import from the barrel: `import { Button, Card, Chip, ... } from '@/components/ui'`.

| Component      | Purpose                                                        |
| -------------- | -------------------------------------------------------------- |
| `Screen`       | Safe-area page frame (scroll, themed status bar, padding).     |
| `CollapsingHeaderScreen` | Page frame with a **pinned header**. Sticky by default; `collapsible` shrinks the title/avatar on scroll. `headerExtra` pins extra controls (e.g. a day selector). |
| `Icon`         | 24px stroke icon set (`react-native-svg`). `name` is typed.    |
| `Card`         | White surface, hairline border, soft elevation. Optional tap.  |
| `HeroCard`     | Espresso dark hero for a headline number (net pay, worked time) — eyebrow + big tabular amount + optional pill; one restrained coral glow. |
| `EmptyState`   | Friendly empty state (tinted icon + title + subtitle + optional CTA) for no-data sections. |
| `Button`       | CTA. Variants: `primary` (coral gradient), `dark`, `light`, `ghost`, `success`. Built-in light haptic. |
| `Chip`         | Status/meta pill. Optional dot + leading node.                 |
| `Avatar`       | Gradient or tinted initial; optional online dot.               |
| `IconTile`     | Rounded tinted square holding an icon (list leadings, actions).|
| `StatTile`     | Centred KPI tile for stat rows.                                |
| `ProgressBar`  | Thin rounded fill (leave balances).                            |
| `SectionLabel` | Section title row (+ optional action / uppercase eyebrow).     |
| `ScreenHeader` | Page header: optional back, title/subtitle, trailing badge.    |
| `BarChart`     | Weekly bar chart with coral-gradient highlight bars.           |
| `Placeholder`  | Striped stand-in for imagery (maps, receipts, illustrations).  |
| `LiveDot`      | Blinking "live/scanning" status dot (Reanimated).              |
| `PillTabBar`   | Floating pill bottom nav (active tab expands to icon + label).  |
| `TextField`    | Form text input (label, error, themed). Keyboard-aware via `Screen`. |
| `SelectChips`  | Single/multi-select chip group for forms (role, type pickers). |
| `DateField`    | Tap-to-pick date row (native picker). Used in submit forms.    |
| `AsyncBoundary`| (in `layout/`) Gates a data section on a query's loading/error, with retry. |

**Adding a component:** put presentational components in `ui/`, give it a typed
props object, theme every value, export it, and add it to `ui/index.ts`. Prefer
small composable pieces over big configurable ones.

---

## 6. Navigation

- **Root** (`app/_layout.tsx`): loads fonts, hides splash when ready, wraps the
  app in `GestureHandlerRootView` → `SafeAreaProvider` → `AuthProvider`, then a
  headerless `Stack`.
- **Entry** (`app/index.tsx`): reads `useAuth().role` and `<Redirect>`s to the
  right experience (`/onboarding`, `/home`, or `/admin/dashboard`).
- **Tabs** use the custom `PillTabBar`. Each `Tabs.Screen` declares its icon via
  a `tabBarIconName` option that the bar reads. Expo Router's option type
  doesn't know this custom key, so we cast the options object `as any` — this is
  the *only* sanctioned `any`. Pattern:
  ```tsx
  <Tabs.Screen name="home" options={{ title: 'Home', tabBarIconName: 'home' } as any} />
  ```
- **Tab screens** must pass `bottomInset={70}` to `<Screen>` /
  `<CollapsingHeaderScreen>` so content clears the floating nav. Pushed (non-tab)
  screens must not.

### Sticky / collapsing headers
Use `CollapsingHeaderScreen` (in `components/layout`) instead of `Screen` +
`ScreenHeader` whenever the header should stay pinned while the body scrolls:
- **Sticky** (default) — header fixed at full size; only the body scrolls. Pass
  `headerExtra` to pin extra controls under the title (e.g. Schedule's day
  selector + date row, so just the shift list scrolls).
- **Collapsing** (`collapsible`) — header stays pinned *and* the title font,
  eyebrow/subtitle, and accessory (avatar/badge) shrink/fade as you scroll. Used
  on Home and the admin Dashboard. Map the old `badge` → `accessory`, and a
  greeting line → `eyebrow`.
Sticky mode uses RN `stickyHeaderIndices`; collapsing mode uses a Reanimated
scroll handler interpolating header height + element scale.

---

## 7. Auth & role-based routing

`AuthContext` (`src/context/AuthContext.tsx`) does real auth against the API:
`signIn(email, password)` stores the JWTs, `signOut()` revokes + clears them,
and the session is restored on launch via `/api/auth/me`. It exposes
`status` (`loading | authenticated | unauthenticated`), the `employee`, and the
`role`.

**One login; role decides the app.** Both experiences ship in one binary:
`isAdminRole(role)` (**OWNER / HR_ADMIN**) → admin app; **MANAGER + EMPLOYEE** →
staff app (see `app/index.tsx` + the login screen). Managers are approvers who
live in the staff app with an **Approvals inbox** (`app/(staff)/approvals.tsx`),
not the HR dashboard. There is **no client-side gate** — RBAC is enforced
server-side (admin APIs 403 non-managers). (`isManagementRole` = not EMPLOYEE,
still used for "can be an approver".)

> Security lives on the backend (auth + RBAC), never in hiding a screen. If you
> ever want admins to enter through a separate surface, that's a UX choice — do
> it by validating the account's role at the chosen entry point, not by hiding UI.

---

## 8. Data — live API (`src/api`) + React Query

Screens get their data from the Spring Boot backend (`/backend`, see its README)
via a typed client and React Query hooks:

- **`src/api/config.ts`** — resolves the API base URL (env `EXPO_PUBLIC_API_URL`,
  else derives the dev machine's LAN IP from the Metro host so physical devices
  work, else localhost/`10.0.2.2`). API port 8080.
- **`src/api/client.ts`** — `fetch` wrapper. Injects the Bearer token, parses
  JSON, throws `ApiError`, and does single-flight **401 → refresh → retry**; a
  failed refresh clears tokens and triggers sign-out.
- **`src/api/tokenStore.ts`** — access/refresh JWTs in `expo-secure-store`.
- **`src/api/endpoints.ts`** — typed functions per domain (auth, employee,
  attendance, leave, claim, overtime, payroll, calendar, notification,
  schedule). `employeeApi` includes `managers`/`transferOwnership`/`update`.
- **`src/api/queries.ts`** — React Query hooks (`useMe`, `useTodayAttendance`,
  `useLeaveBalances`, `useClaims`, `usePayslips`, `useCalendar`,
  `useNotifications`, `useSchedule`, `useOvertime`, admin `useDashboard`/
  `useLiveAttendance`,
  `useStaff`, `useManagers`, `usePendingLeave`/`usePendingClaims`/
  `usePendingOvertime`, mutations `useClockIn`/`useDecideLeave`/
  `useAssignShift`/`useTransferOwnership`/`useMarkAllRead`/…).
- **`src/api/types.ts`** mirror backend DTOs; **`src/api/accents.ts`** maps
  status/accent keys → theme colours.

Auth lives in `AuthContext` (real login, secure tokens, session restore on
launch). The `<QueryClientProvider>` wraps the app in the root layout. Use
`<AsyncBoundary loading error onRetry>` (in `components/layout`) to gate
data-dependent sections.

**Notifications & push** (`src/notifications`): submitting/deciding a request
auto-creates an in-app notification (`useNotifications` feed) on the backend,
which also fans out an **Expo push** to the recipient's registered devices.
`push.ts` registers/unregisters the device token (wired into `AuthContext`
sign-in / session-restore / sign-out; **no-ops in Expo Go & simulators** — push
delivery needs a dev/EAS build). `NotificationListeners` (mounted at the root)
refetches the feed on a foreground receipt and opens Notifications on tap.

**Everything user-facing is API-backed.** The admin **Dashboard** (KPIs, week
chart, recent-activity feed) comes from `GET /api/admin/dashboard` (`useDashboard`);
the staff **Home** "Today's summary" tiles read real attendance/leave/claim data
(no dedicated endpoint — composed client-side from `useTodayAttendance` +
`useLeaveBalances` + `useClaims`). No screen ships hard-coded sample numbers — if
a stat is shown, it's real (golden rule §0).

---

## 9. Worklog (`/worklog`)

Every meaningful change gets a dated markdown entry: `worklog/YYYY-MM-DD-slug.md`.
Capture **what** changed, **why**, and anything non-obvious for the next person.
This is the project's narrative memory — keep it honest (note skips/TODOs).

---

## 10. Commands

```bash
npm install            # install deps
npm run start          # Expo dev server (press i / a / w)
npm run ios            # iOS simulator
npm run android        # Android emulator
npm run typecheck      # tsc --noEmit
npx eas build -p ios --profile preview      # EAS build (needs eas login)
```

---

## 11. Conventions cheat-sheet

- Functional components, hooks only. Default-export screens.
- Status badge = map status → `{ color, bg }`, feed `Chip color/background`.
- List dividers: rows after the first get `borderTopWidth: 1` + `palette.line`.
- Money/numbers are plain `Text` (Figtree numerals read evenly). No extra libs.
- Keep imports ordered: react → RN → 3rd-party → `@/...` → relative.
- Light haptics on primary actions (already built into `Button`/`PillTabBar`).
- **Keyboard handling is automatic** — it's built into `Screen` /
  `CollapsingHeaderScreen` (focused input stays above the keyboard; tap-outside
  and drag dismiss it). Build forms with `Screen` + the form kit (`TextField`,
  `SelectChips`, `DateField`); **never** add your own `KeyboardAvoidingView`.

---

## 12. Backend & database (`/backend`)

Spring Boot 3.3 · Java 21 · PostgreSQL 16 · JWT auth. **Multi-tenant** (one DB,
companies isolated by `company_id`). Runs via Docker — no local Java/Maven
needed. Full details in `backend/README.md`.

- **Run:** `cd backend && docker compose up --build` (Postgres + API + pgAdmin).
  API on `:8080`, Swagger at `/swagger-ui.html`. DB host port **5435**.
- **Config:** `backend/.env` (gitignored; copy from `.env.example`). Compose
  reads it via `${VAR}` substitution; `application.yml` reads the same env names.
- **Layout:** package-by-feature under `com.teamora` (`auth`, `company`,
  `employee`, `attendance`, `leave`, `claim`, `overtime`, `payroll`, `calendar`,
  `schedule`, `notification`, + `config`, `security`, `common`, `seed`).
- **Migrations:** currently `V1`–`V10` (init → multi-tenancy & roles →
  reporting-manager + single-owner → overtime → schedule → company events →
  notifications → push tokens → employee monthly_salary → tax profile + payslip PCB).

### Multi-tenancy & roles
- The **company** is the tenant. Every tenant-scoped entity extends
  `common.TenantEntity` (adds a non-null `company_id`). Identity tables
  (`employees`, email globally unique) and `refresh_tokens` are the exception
  to the "always filter by company" rule because login is cross-tenant.
- **Scoping is explicit:** services set `company` on every row they create and
  filter admin/cross-employee queries by the caller's company id
  (`currentEmployee.require().getCompany().getId()`). "My …" reads are safe via
  `employeeId` (an employee belongs to one company). Single-record admin actions
  (approve/reject) verify the row's company matches the admin's.
- **Roles** (`employee.Role`): `OWNER`, `HR_ADMIN`, `MANAGER`, `EMPLOYEE`.
  `/api/admin/**` → OWNER/HR_ADMIN/MANAGER; employee + company management →
  OWNER/HR_ADMIN. The app routes **OWNER/HR_ADMIN → admin app**, MANAGER +
  EMPLOYEE → staff app (managers get an Approvals inbox).
- **Exactly one OWNER per company** (partial unique index
  `uq_employees_one_owner_per_company` + service guards). OWNER can't be set via
  create/update/changeRole — only via `POST /api/employees/{id}/transfer-ownership`
  (owner-only), which demotes the current owner to HR_ADMIN and promotes the
  target (demote→flush→promote so the index never transiently breaks).
- **Reporting manager:** `employees.reporting_manager_id` (self-FK, nullable) —
  set via the add/edit forms (`GET /api/employees/managers` lists assignable
  OWNER/HR_ADMIN/MANAGER). **Approvals route to it:** a MANAGER sees/decides only
  their direct reports' leave/claims; HR_ADMIN/OWNER override (see/decide
  anything); a null reporting manager defaults the approver to the company OWNER.
  Decide endpoints throw 403 (`AccessDeniedException`) if the caller isn't the
  resolved approver.
- **Onboarding:** `POST /api/auth/register` (public) creates a company + its
  OWNER and signs in. Admins add staff via `POST /api/employees`.
- **Demo** (idempotent seed; password `password`): company *Lumi Foods* —
  `owner@lumi.com` (OWNER), `sarah@lumi.com` (HR_ADMIN), `nadia@lumi.com`
  (MANAGER), `amir@lumi.com` (EMPLOYEE, full data) + others; company *Nusantara
  Tech* — `admin@nusantara.com` (OWNER) + staff (proves isolation).

### Payroll engine
Payroll is a **real run**, not seeded numbers. `POST /api/admin/payroll/run`
(OWNER/HR_ADMIN; MANAGER is excluded) generates DRAFT payslips for a period from
each active, salaried employee's `monthly_salary` + that period's **approved
overtime** (EA hourly OT = monthly/26/8 × 1.5) + **approved claims**.
`PayrollCalculator` (pure, unit-tested to the sen) computes Malaysian statutory
deductions: **EPF** 11% (employer 12/13%) rounded up to the ringgit on basic+bonus;
**SOCSO/EIS** at gazetted Cat-1 rates on the RM100 wage-band midpoint capped at the
RM6,000 ceiling, with OT/bonus excluded from the SOCSO/EIS base and claims excluded
from every base. **PCB/MTD income tax is estimated** via the LHDN computerised
method (YA2024 brackets + reliefs) from each employee's tax profile
(`marital_status` / `spouse_working` / `num_children`), annualising basic salary;
it carries **no** year-to-date accumulation, so it's labelled "PCB (est.)" in the
UI — a real estimate, never presented as a filed figure. Lifecycle:
run → `…/approve` (DRAFT→APPROVED) → `…/mark-paid` (APPROVED→PAID); re-running is
idempotent and never clobbers an APPROVED/PAID payslip.

### Database migrations (Flyway)
- **Flyway owns the schema; JPA runs `ddl-auto: validate`** — Hibernate never
  alters tables. Every schema change is a migration file.
- Migrations live in `backend/src/main/resources/db/migration/`, named
  `V<n>__<desc>.sql` (double underscore, version strictly increasing).
- **They run automatically on API startup** — applied state is tracked in the
  `flyway_schema_history` table; only new files are applied.
- **Adding one:** create `V2__….sql`, then **rebuild the image** so it's bundled:
  `docker compose up -d --build api`. A plain stop/start of the *existing*
  container does NOT pick up new files (the jar + migrations are baked into the
  image at build time) — you must rebuild.
- **Never edit an applied migration** (checksum mismatch fails startup) — add a
  new `V<n>`.
- **Reset dev DB:** `docker compose down -v && docker compose up -d` (wipes the
  volume → all migrations re-apply + seeders re-run).
- If you add an entity field, add the matching column in a migration or
  `validate` mode refuses to start (intentional safety net).

---

## 13. Testing & checks — definition of done

**Every new endpoint gets at least one integration test** before it's "done" —
covering the happy path, the auth/permission outcome (401 unauthenticated / 403
forbidden), and key validation. Run the checks below before wrapping up any
change; CI runs the same.

**Backend tests** live in `backend/src/test/java`, named `*IT`, extend
`AbstractIntegrationTest` (boots the full app + security chain + Flyway against a
real Postgres; demo data seeded), and drive endpoints with `MockMvc`. Example:
`auth/AuthAndTenantIT` (login, RBAC, tenant isolation, signup) and
`leave/LeaveFlowIT` (apply → admin approve).

**Run the checks (no local Java/Maven needed):**
```bash
# App — type safety (from repo root)
npm run typecheck

# Backend — compiles + Flyway + JPA validation + all tests, in Docker,
# against a throwaway `teamora_test` DB (needs the db container up).
cd backend && ./scripts/test-backend.sh        # = mvn verify
```
- `scripts/test-backend.sh` runs `mvn verify` in a Maven container on the Compose
  network; `*IT` tests run via the Failsafe plugin. Dev data is never touched.
- `docker compose build` compiles but **skips** tests — the script is the test gate.
- **API convention:** unauthenticated → **401**, authenticated-but-forbidden →
  **403** (enforced by the security entry point).
