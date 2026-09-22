# 2026-09-19 — Playbook gap review + quick wins

Reviewed the project against `NEW_PROJECT_PLAYBOOK.md` (generic Boardly-based
standard) and closed the cheap, high-value gaps. Bigger gaps (i18n, dark mode)
noted for follow-up.

## Done (quick wins)
- **`eas.json` env coverage** (playbook §2.1 "every profile sets APP_ENV +
  EXPO_PUBLIC_API_URL"):
  - `development` — added `APP_ENV=development` + `ios.simulator: true`
    (simulator dev client). No `EXPO_PUBLIC_API_URL` on purpose — dev relies on
    `src/api/config.ts` Metro/LAN host derivation.
  - `development-device` — new profile: physical-device dev client,
    `APP_ENV=development`.
  - `preview` — already carries the ngrok URL.
  - `production` — added `APP_ENV=production` + a clearly-marked
    `EXPO_PUBLIC_API_URL` placeholder (`REPLACE-WITH-PRODUCTION-API-URL`) to fill
    when the prod API is deployed.
- **Accessibility props** (playbook §2.4 — was zero across `src`): added to every
  interactive kit component —
  - `Button` → `role=button`, label, `state.disabled`
  - `SelectChips` → `role=radio`, label, `state.selected`
  - `DateField` → `role=button`, label (field + value), hint
  - `Card` (when `onPress`) → `role=button`
  - `ScreenHeader` → back (`role=button`, "Go back") + text action
  - `PillTabBar` → `role=tab`, label, `state.selected`
- **README** — removed the stale "press w (web)" (web was dropped from
  `platforms` in `app.config.ts`).

Verified: `npm run typecheck` clean; `eas.json` parses.

## Deliberate deviations (not fixed — by design)
- **Dark mode / semantic light+dark ColorScheme + ThemeProvider** (§2.3): CLAUDE.md
  is "light mode only (for now)". Left as-is.
- **Structure**: routes at repo-root `app/` (not `src/app/`), no `features/` or
  `games/` registry — Teamora's documented convention (screens + hooks + `api/`
  React Query). Fine.
- **Backend**: Spring Boot / Flyway, not Flask / Alembic (§3). Valid equivalent.

## Next real gap — i18n (playbook §2.5), NOT yet done
Biggest genuine gap for a Malaysia-focused HR app. Scope:
1. Deps: `npx expo install i18next react-i18next expo-localization @react-native-async-storage/async-storage`.
2. `src/i18n/` — `index.ts` (i18next init + device detect), `LocaleContext.tsx`
   (persisted `useLocale`), `locales/{en,ms,zh}.json`.
3. Wire `LocaleProvider` into `app/_layout.tsx` provider tree.
4. Extract all hardcoded user-facing strings → `t('scope.key')`, keys mirroring
   screens (`home.*`, `payroll.*`, `approvals.*`, `common.*`).
5. Language switcher in Profile (staff + admin), beside a future theme switcher.
6. Backend: check server-sent user-facing strings (notification titles/bodies,
   validation messages) — decide whether to localise client-side by key or send
   locale to the API. **Note:** many statuses/labels already come from the API;
   this needs a pass so translated screens don't show English API strings.
