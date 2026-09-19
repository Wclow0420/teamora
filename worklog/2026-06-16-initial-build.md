# 2026-06-16 — Initial build: Warm & Human HR app (Staff + Admin)

## What we built

Scaffolded **Teamora** from scratch: a React Native + Expo Router (TypeScript)
app implementing the approved **"Warm & Human"** design direction from the
Claude Design handoff bundle. One binary hosts both the **Staff** (employee) and
**Admin** (manager/HR) experiences.

### Foundation
- Expo SDK 52 project config: `package.json`, `app.json`, `eas.json`,
  `tsconfig.json` (with `@/* → src/*` alias + typed routes), `babel.config.js`,
  `.gitignore`.
- **Theme system** (`src/theme`): `colors` (palette, tints, gradients, accent
  maps), `typography` (Figtree weights + presets), `spacing`/`radius`,
  warm-toned `shadows`. Single import surface via `theme`.
- **UI kit** (`src/components/ui`): `Icon` (full 24px stroke set ported to
  `react-native-svg`), `Card`, `Button` (5 variants + haptics), `Chip`,
  `Avatar`, `IconTile`, `StatTile`, `ProgressBar`, `SectionLabel`,
  `ScreenHeader`, `BarChart`, `Placeholder`, `LiveDot`.
- **Layout**: `Screen` (safe-area frame, themed status bar, scroll).
- **Navigation**: custom `PillTabBar` — the redesigned floating pill nav (active
  tab expands to icon + label). Drives both staff and admin tabs.
- **State**: `AuthContext` (role: staff/admin + secret-portal unlock).
- **Hooks**: `useLiveTimer` (live work timer), `useAuth`.
- **Data**: `src/data/mock.ts` (`ME`, `ADMIN`, typed `STAFF`, `STATUS`).

### Screens (16 total)
- **Auth**: onboarding, login (with secret admin portal).
- **Staff tabs**: home, attendance, calendar, payroll, profile.
- **Staff pushed**: clock-in (face-ID, full-bleed dark), leave, claims,
  notifications.
- **Admin tabs**: dashboard, live attendance, staff, approvals, payroll.
- **Admin pushed**: schedule (hidden from nav, reached from dashboard).

### Secret admin portal
The public login only offers staff sign-in. Tapping the **lumi logo 5×** flips
`adminUnlocked` and reveals the admin-entry card. Documented as a prototype-only
gate; production must use real RBAC (see `CLAUDE.md` §7).

## How we worked
- Read the design handoff (chat transcript confirmed the user chose **Warm &
  Human** direction A) and ported all 16 screens 1:1 from the prototype JSX.
- Built the design system + first screen (home) by hand to set the pattern, then
  fanned the remaining 14 screen ports out to parallel agents against a shared
  design-system contract for consistency.

## Notes / follow-ups
- App icons/splash images are not included (config references colours only) —
  add real asset files before an EAS production build.
- Data is mocked; wire a real API/auth layer next (intended: typed client +
  React Query). Replace the tap-to-unlock admin gate with server-issued roles.
- `eas.json` uses a placeholder `projectId` — run `eas init` to set the real one.
