# Teamora

A warm, human HR & Payroll mobile app for Malaysia (RM, EPF/SOCSO/EIS) — built
with **React Native + Expo Router + TypeScript**, released via **EAS**.

One app, two experiences:

- **Staff** — onboarding, face-ID clock-in, attendance history, leave, claims,
  payslip, company calendar, notifications, profile.
- **Admin** — dashboard, live attendance, staff directory, approvals, payroll
  processing, scheduling.

Both share one **"Warm & Human"** design system (cream + coral, Figtree).

## Quick start

```bash
npm install
npm run start      # then press i (iOS) or a (Android)
```

## The secret admin portal

The login screen only shows staff sign-in. To reveal the **Admin portal**, tap
the **lumi logo 5 times** on the login screen — an admin card appears with an
*Enter admin dashboard* action. (Prototype gate only — see `CLAUDE.md` §7 for
the production note.)

## Project layout

See **[CLAUDE.md](./CLAUDE.md)** for the full architecture, theme system,
component kit, and conventions. Change history lives in **[worklog/](./worklog)**.

## Scripts

| Command             | What                          |
| ------------------- | ----------------------------- |
| `npm run start`     | Expo dev server               |
| `npm run ios`       | iOS simulator                 |
| `npm run android`   | Android emulator              |
| `npm run typecheck` | `tsc --noEmit`                |
