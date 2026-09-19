# 2026-09-19 — Migrate to `app.config.ts` + EAS Update readiness

## What changed
- **Removed `app.json`**, replaced with **`app.config.ts`** (dynamic Expo config).
  - Single source of truth; `version` read from `package.json` so app + build stay in lockstep.
  - **Per-environment identity** via `APP_ENV` (`development` / `preview` / `production`):
    separate `name`/`package`/`bundleIdentifier`/`scheme` suffixes (`.dev`, `.preview`)
    so dev/preview/prod installs coexist on one device. Production keeps the
    original identity: name `Teamora`, id `com.teamora.app`, scheme `teamora`.
  - Preserved existing settings: `owner: teamora`, EAS `projectId`
    `ce9d1300-4852-4008-bf9b-3e680add6f84`, `newArchEnabled`, `light` UI style,
    cream (`#FBF5EE`) splash + adaptive-icon background, and the same plugin list
    (expo-router, expo-font, expo-asset, expo-secure-store, datetimepicker,
    expo-notifications).
  - **Dropped the `web` platform** → `platforms: ['ios','android']`. `react-native-web`
    isn't installed, so leaving web in config would make `eas update` export a web
    bundle and fail the whole update.
  - **Added EAS Update config** that was previously missing: `updates.url`
    (`https://u.expo.dev/<projectId>`) and `runtimeVersion` policy `appVersion`.

## EAS Update readiness — before vs after
Before: **not ready.** No `expo-updates` package, no `updates.url`, no
`runtimeVersion`, and `web` in config would break the export.

Fixed here:
- Installed **`expo-updates`** (SDK 54 compatible).
- Added `updates.url` + `runtimeVersion` in `app.config.ts`.
- Removed web from platforms.

Verified: `npx expo config --type public` resolves cleanly (scheme/package
suffix correctly by APP_ENV; updates + runtimeVersion present); `npm run
typecheck` passes; `expo-doctor` 17/18 (only pre-existing patch-version drift on
`expo`/`expo-constants`).

## Still required before an OTA update actually reaches users
1. `eas login` with access to the `teamora` owner/project.
2. A **new native build per channel** made *with* `expo-updates` baked in and the
   same `runtimeVersion` (1.0.0). Existing older builds (if any) can't receive
   these updates — OTA only matches builds that shipped with expo-updates + the
   matching runtimeVersion.
3. Then: `eas update --branch <production|preview|development> -m "…"`.

## Notes / TODO
- `expo`/`expo-constants` are one patch behind SDK 54 — run
  `npx expo install --check` to align when convenient (unrelated to this change).
- `extra.apiUrl` is now exposed, but `src/api/config.ts` still reads
  `process.env.EXPO_PUBLIC_API_URL` directly (unchanged) — set it per profile in
  `eas.json` for real builds.
