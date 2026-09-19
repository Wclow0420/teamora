# New Project Playbook

The standard setup **every new app** follows. It is written generically —
replace names/ports per project. Boardly is the reference implementation;
when in doubt, copy from it.

---

## 1. Repository layout

```
<project>/
├── backend/            # API server (one deployable unit)
├── <app>/              # Mobile app (Expo / React Native)
├── README.md           # How to run everything, game/module flow
└── NEW_PROJECT_PLAYBOOK.md
```

One repo, frontend and backend side by side. Each is independently
runnable and has no imports across the boundary — they talk only through
the HTTP/WebSocket API.

---

## 2. Frontend (Expo / React Native)

### 2.1 Bootstrap

```bash
npm install --global eas-cli
npx create-expo-app <app>
cd <app> && eas init --id <EAS_PROJECT_ID>
```

Then **delete `app.json`** and use:

- **`eas.json`** — build profiles: `development` (simulator, dev client),
  `development-device`, `preview` (internal APK / TestFlight), `production`.
  Every profile sets `APP_ENV` and `EXPO_PUBLIC_API_URL`.
- **`app.config.js`** — dynamic config keyed on `process.env.APP_ENV`:
  app name (`App Dev` / `App Preview` / `App`), bundle id
  (`com.x.app.dev` / `.preview` / base), scheme, icons. Version read from
  `package.json`. `extra.eas.projectId`, `owner`, `updates.url`,
  `runtimeVersion: { policy: "appVersion" }`.

Rules:
- Never hardcode API URLs in code — always `process.env.EXPO_PUBLIC_API_URL`
  with a localhost fallback for dev.
- Different bundle id per environment so dev/preview/prod install side by side.

### 2.2 Folder structure (`src/`)

```
src/
├── app/                  # expo-router routes ONLY (thin — compose features)
│   ├── _layout.tsx       #   fonts + all providers + root Stack
│   ├── (tabs)/           #   tab screens
│   └── <flow>/[param].tsx
├── theme/                # design system source of truth
│   ├── tokens.ts         #   palette, spacing, radius, typography, shadows
│   ├── colors.ts         #   SEMANTIC ColorScheme: light + dark objects
│   ├── ThemeContext.tsx   #   ThemeProvider, useTheme(), useThemeMode()
│   └── index.ts          #   barrel
├── i18n/
│   ├── index.ts          #   i18next init + SUPPORTED_LANGUAGES + device detect
│   ├── LocaleContext.tsx  #   LocaleProvider, useLocale() (persisted)
│   └── locales/          #   en.json, zh.json, ms.json … (keys mirror screens)
├── components/
│   ├── ui/               #   REUSABLE design-system components (see 2.4)
│   ├── icons/            #   SVG icon set (react-native-svg), themed via props
│   └── navigation/       #   TabBar and other chrome
├── features/             # one folder per screen/domain
│   └── <feature>/        #   hooks.ts (data) + presentational components
├── games/ (or modules/)  # one folder per game/module: logic + UI + index
│   ├── types.ts          #   shared contracts (GameDefinition, BoardProps)
│   ├── registry.ts       #   explicit registry — mirrors backend registry
│   └── <key>/            #   logic.ts, <Key>Board.tsx, index.ts
├── context/              # app-wide contexts (SessionContext, …)
├── hooks/                # generic reusable hooks (not feature-specific)
├── api/                  # client.ts (typed REST), socket.ts (realtime)
└── data/                 # types.ts (domain models), mock.ts (until API wired)
```

Principles:
- **Routes are thin.** Files in `app/` only compose feature components and
  hooks. No business logic, no fetch calls, no styles beyond layout glue.
- **Features own their data hooks.** `features/x/hooks.ts` is the seam:
  screens never import mock data or the API client directly, so swapping
  mock → real API touches one file per feature.
- **Per-module folders** (games here): logic and UI live together, exposed
  through a typed registry. Adding a module = new folder + one registry line.

### 2.3 Theme (mandatory pattern)

- `tokens.ts` holds raw values (palette hexes, spacing scale, radius scale,
  Poppins/whatever type ramp, shadow presets). **No component imports raw
  palette hexes for surfaces/text** — brand-fixed elements (gradient heroes,
  logo art) are the only exception.
- `colors.ts` defines a **semantic** `ColorScheme` interface (background,
  surface, card, border, text, textMuted, primary, success, …) with a
  `lightColors` and `darkColors` object. Dark mode is designed here once,
  free everywhere.
- `ThemeContext` exposes `useTheme()` → `{ colors, spacing, radius,
  typography, shadows, isDark }` and `useThemeMode()` → `light | dark |
  system`, persisted in AsyncStorage.
- Fonts loaded once in root `_layout.tsx` (`@expo-google-fonts/*`), splash
  screen held until ready.

### 2.4 Reusable UI kit (`components/ui/`)

Build these before building screens, from the design's component sheet:

`AppText` (typography variants + semantic colors) · `Screen` (safe-area +
scroll + padding) · `Button` (primary/secondary/tertiary × lg/md/sm,
loading state) · `Card` · `Chip` (selectable pill) · `Toggle` · `StatusPill`
(tinted badges) · `Avatar` (initial + deterministic tint + presence dot +
crown) · `AvatarStack` (+N overflow) · `IconButton` · `SectionHeader`.

Rules:
- Screens **never** restyle these ad-hoc; if a variant is missing, add it
  to the component.
- Every component reads `useTheme()` — zero hardcoded colors.
- `style` props are `StyleProp<ViewStyle>`.
- Accessibility props (`accessibilityRole`, `accessibilityState`) on every
  interactive component.

### 2.5 i18n (mandatory pattern)

- `i18next` + `react-i18next` + `expo-localization` (device detect) +
  AsyncStorage persistence via `LocaleProvider`.
- **No user-visible string literals in components.** Everything goes
  through `t("scope.key")`; interpolation for counts
  (`t("lobby.playersCount", { current, max })`).
- Locale files mirror screen structure (`home.*`, `games.*`, `profile.*`,
  `common.*`). Add all supported languages when adding a key — never let
  languages drift.
- Language switcher lives in Profile/Settings alongside the theme switcher.

### 2.6 Contexts & hooks

- Provider order in root `_layout.tsx`:
  `GestureHandlerRootView → SafeAreaProvider → ThemeProvider →
  LocaleProvider → SessionProvider → navigation`.
- Every context ships a `useX()` hook that **throws** outside its provider.
- Contexts hold cross-cutting state only (theme, locale, session).
  Feature/server state lives in feature hooks (upgrade path: TanStack Query).

### 2.7 Quality gates

- TypeScript `strict`; `npx tsc --noEmit` must be clean before commit.
- Path alias `@/*` → `./src/*`; no `../../..` imports.
- Barrel exports (`index.ts`) for `ui/`, `theme/`, `icons/`.

---

## 3. Backend (Flask + PostgreSQL)

```
backend/
├── app/
│   ├── __init__.py       # create_app() factory
│   ├── config.py         # env-driven Config
│   ├── extensions.py     # db, migrate, cors, socketio singletons
│   ├── models/           # SQLAlchemy models
│   ├── routes/           # blueprints (thin — validate, call domain, emit)
│   ├── sockets/          # Socket.IO events
│   └── games/ (modules/) # one folder per module + base.py contract + registry
├── migrations/           # Alembic via Flask-Migrate
├── docker-compose.yml    # api + postgres (project-specific host ports)
├── Dockerfile
├── requirements.txt      # pinned versions
└── .env.example
```

- App-factory pattern; extensions initialized in `create_app`.
- Module state stored as JSONB; engines are **stateless** classes
  implementing a shared `Base` contract, registered in an explicit dict
  that mirrors the frontend registry.
- Docker Compose runs db + api with a healthcheck gate; pick unique host
  ports per project (Boardly: db **5439**, api **5005**).
- Migrations: `flask db migrate -m "..."` + `flask db upgrade` inside the
  container. Never edit applied migrations.

---

## 4. Design → code workflow

1. Import/read the design (Claude Design project, Figma, …).
2. Extract **tokens first**: palette, type ramp, radii, shadows → `theme/`.
3. Build the **UI kit** from the design's component sheet.
4. Draw custom **SVG icons** matching the design's stroke style
   (react-native-svg) — no icon-font grab-bag mixing styles.
5. Compose screens from kit + feature components; verify against the
   design side by side (light **and** dark, at least 2 languages).

---

## 5. New-project checklist

- [ ] Repo layout (§1); backend scaffold (§3) with unique ports
- [ ] Expo app created, `app.json` → `eas.json` + `app.config.js` (§2.1)
- [ ] `src/` skeleton (§2.2) — delete template demo files
- [ ] Theme: tokens + semantic light/dark + provider (§2.3)
- [ ] i18n: locales + provider + device detect (§2.5)
- [ ] UI kit components (§2.4) before any screen
- [ ] Providers wired in root layout (§2.6)
- [ ] Feature folders with `hooks.ts` data seams
- [ ] Module/game registries on both sides
- [ ] `tsc --noEmit` clean; light/dark + all languages smoke-tested
- [ ] README: run instructions, ports, how to add a module

---

## 6. Dependency baseline (frontend)

| Purpose    | Package |
|------------|---------|
| Navigation | `expo-router` (custom TabBar via `expo-router/js-tabs`) |
| Theme persistence | `@react-native-async-storage/async-storage` |
| i18n       | `i18next`, `react-i18next`, `expo-localization` |
| Fonts      | `expo-font`, `@expo-google-fonts/<family>` |
| Icons      | `react-native-svg` |
| Gradients  | `expo-linear-gradient` |
| Clipboard  | `expo-clipboard` |
| Realtime   | `socket.io-client` |

Install Expo-managed packages with `npx expo install` (never bare
`npm install`) so versions match the SDK.
