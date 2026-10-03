# Teamora — Manual QA Routine (master test script)

Run this top to bottom before every release. It covers **every screen and every
interactive control** in both experiences that ship in the one binary: the
**Staff app** (EMPLOYEE + MANAGER) and the **Admin app** (OWNER + HR_ADMIN).

Screen names, button labels and error messages below are copied from the code
(`app/**`, `src/components/**`, backend services). If the app shows different
wording, that is either a bug or this document is stale — fix one of them.

---

## 0. How to use this document

### 0.1 Purpose
- Prove that a real employee and a real manager can do their whole job in the app.
- Catch regressions the automated suite cannot see: layout, wording, navigation,
  device permissions, money shown on screen, and cross-screen consistency.
- Leave a signed-off record: tick the checkbox on each case, note the build and date.

### 0.2 Two kinds of pass

| Pass | When | What to run | Time |
| --- | --- | --- | --- |
| **Smoke** | Every OTA update, every backend deploy, before handing a build to anyone | Section **H** only | ~15 min |
| **Full regression** | Before every store / preview binary, after any migration, after any payroll or leave-engine change | Sections **A → G** in order, then **H** on the final build | ~3–4 h (one tester, two devices) |

Rules for a pass:
1. Run the automated gate first (0.6). Do not start manual QA on a red build.
2. Reset the test data (0.5) so balances and payroll start from a known state.
3. Work in order — later sections reuse data created by earlier ones (noted in *Pre*).
4. A case fails if the **Expect** line is not met exactly, a screen shows a fake /
   placeholder number, text is clipped, or a control does nothing.
5. Log every failure with: case ID, build, device + OS, account, screenshot.

### 0.3 Environments and builds

| Environment | How to get it | Use it for | Cannot test |
| --- | --- | --- | --- |
| **Expo Go** (`npm run start`, scan QR) | Dev machine + phone on the same Wi-Fi; API auto-resolves to the Mac's LAN IP on port 8080 | Most flows: auth, leave, claims, overtime, calendar, payslip + PDF, all admin screens | Remote push (registration is skipped in Expo Go) |
| **iOS Simulator** (`npm run ios`) | Xcode simulator | Layout, navigation, forms, simulated location (Features → Location) | **No camera** (the clock-in selfie and claim receipt capture must be tested on a physical device); push registration is skipped |
| **Android emulator** (`npm run android`) | API base falls back to `10.0.2.2:8080` | Android layout, back button, keyboard | Push |
| **Preview build** (`npx eas build -p ios\|android --profile preview`) | Internal distribution; `APP_ENV=preview`; API URL comes from `eas.json` (currently an ngrok tunnel — it must be running) | Everything native: selfie camera, claim receipt camera, real GPS geofence, push delivery, share sheet, file export | — |
| **Production build** (`--profile production`) | `APP_ENV=production` | Final smoke; confirm no demo credentials on the login screen | — |

Backend: `cd backend && docker compose up --build` → API `:8080`, Swagger
`/swagger-ui.html`, Postgres host port `5435`.

### 0.4 Test accounts (password for all: `password`)

| Email | Name | Role | Lands in | Use for |
| --- | --- | --- | --- | --- |
| `owner@lumi.com` | Imran Yusof | OWNER | Admin app | Ownership transfer, fallback approver (null manager), everything admin |
| `sarah@lumi.com` | Sarah Lim | HR_ADMIN | Admin app | Day-to-day admin, payroll, settings |
| `nadia@lumi.com` | Nadia Rahman | MANAGER | Staff app + Approvals inbox | Direct-report approvals (Amir, Arjun, Siti report to her) |
| `amir@lumi.com` | Amir Hakim | EMPLOYEE | Staff app | Richest data: RM 4,000 salary, married / spouse not working / 2 children, balances 16/14/5 |
| `arjun@lumi.com` | Arjun Nair | EMPLOYEE | Staff app | Second direct report of Nadia (RM 4,100) |
| `admin@nusantara.com` | Daniel Wong | OWNER of *Nusantara Tech* | Admin app | Tenant-isolation checks |

Other seeded Lumi staff (all `password`): `weijie@` (RM 7,000), `priya@`,
`faizal@` (RM 2,800), `meiling@` — these four report to **Sarah**; `siti@` reports
to **Nadia**. Sarah and Nadia have **no** reporting manager, so their own requests
go to the **owner**. Second tenant also has `budi@nusantara.com` (EMPLOYEE).

Seeded leave types per company: **Annual Leave** (16, paid), **Medical Leave**
(14, paid), **Emergency Leave** (5, paid), **Unpaid Leave** (0, unpaid, accrual
None). No work locations are seeded — every demo employee starts **unassigned**
(clock in anywhere) until you create and assign a site.

### 0.5 Test-data reset

```bash
cd backend
docker compose down -v && docker compose up -d   # wipes the DB volume, re-applies all Flyway migrations, re-seeds
```

- Do this before every **full regression**. Seeding is idempotent, so a plain
  restart does **not** undo test data — only `down -v` does.
- After adding a migration you must rebuild the image: `docker compose up -d --build api`.
- The "fast jar hot-swap" used during development (`backend/Dockerfile.fast`, or
  `docker cp` of the jar + `docker restart`) is a **dev-only shortcut**. Never QA
  a release candidate against a hot-swapped container — rebuild the image so the
  jar and migrations under test are the ones that will ship.
- Seeded leave requests are dated June 2026; do not rely on seeded rows appearing
  under "this month". Create fresh data as the cases instruct.

### 0.6 What the automated tests already prove

Run before manual QA:

```bash
npm run typecheck                          # app — TypeScript strict
cd backend && ./scripts/test-backend.sh    # mvn verify: compile + Flyway + JPA validate + all tests
```

Manual QA does **not** need to re-prove the API rules below — it must prove the
**UI respects and explains them**.

| Area | Test classes | Already covered |
| --- | --- | --- |
| Auth, RBAC, tenancy | `auth/AuthAndTenantIT` | Login returns role + company; bad credentials 401; employee 403 on admin APIs; unauthenticated 401; register creates company + owner isolated from other tenants; duplicate email rejected |
| Passwords | `auth/PasswordIT` | Change password: 401; wrong current / too short / missing / same-as-current → 400; new password signs in and the old one fails; all sessions revoked, except the one whose refresh token is sent; cannot spare someone else's session. Reset: HR resets an employee who can then sign in and is signed out; manager + employee 403; HR cannot reset the owner (403); owner can reset HR; too short 400; cross-tenant 404 |
| Approval routing | `approval/ApprovalRoutingIT` | Manager sees only direct reports; cannot approve a non-report (403) while the owner can; owner sees all pending |
| Clock-in selfie | `attendance/ClockInPhotoIT` | Photo stored and flagged on live board; photo readable by self and same-company admin; other company 403; unauthenticated 401; data-URL prefix handled; > 2 MB rejected; clock-in without photo works |
| Geofence | `attendance/GeofenceClockInIT`, `common/GeoUtilTest` | Inside radius OK; outside radius rejected with distance message; assigned but no coordinates rejected; unassigned clocks in without coordinates; inactive site = no geofence; distance maths |
| Calendar | `calendar/CalendarIT`, `calendar/CalendarAdminIT` | Month grid + upcoming; default month; HR creates/patches/deletes a holiday and it appears on both calendars; manager read-only, employee no access; blank title / missing date / BIRTHDAY type rejected; tenant-scoped (foreign admin 404); 401s |
| Dashboard | `dashboard/DashboardIT` | HR admin summary; manager allowed; employee 403; unauthenticated 401 |
| Employees | `employee/EmployeeManagementIT`, `employee/EmployeePartialUpdateIT`, `employee/OwnershipTransferIT` | Update name/title/department/role; cannot change own role; cross-tenant update blocked; employee cannot update; reporting manager must be managerial; **partial PATCH leaves manager + work location untouched**; explicit clear flags; id wins over clear flag; cannot report to self; cross-company manager / location ids 400; ownership transfer swaps roles; non-owner 403; no second owner via create / update / change-role |
| My details | `employee/SelfProfileIT` | `PATCH /api/employees/me` trims, saves and clears the caller's phone; every other field in the body is ignored; phone > 32 characters 400; employee still 403 on the admin update; 401 |
| Claim receipts | `claim/ClaimReceiptIT` | Claim with a receipt is flagged on the list and the approvals queue; claimant and approvers can fetch the photo; data-URL prefix handled; unrelated employee and non-approving manager 403; other company cannot fetch; 401; oversized / invalid photo 400 and no claim created; claim without a receipt works and its receipt is 404 |
| Leave engine | `leave/LeaveFlowIT`, `leave/LeaveTypeConfigIT`, `leave/PartialLeaveIT`, `leave/LeaveAccrualIT`, `leave/LeaveYearTest` | Apply → pending → approve; employee cannot approve; leave-type CRUD RBAC; half day = 0.5; 2 h of 8 h = 0.25; full-day range counts working days only; half-day / hours reject a date range; hours must be > 0 and ≤ a working day; range with no working days rejected; leave on a public holiday rejected; end before start rejected; fixed / monthly / none accrual; join-date proration; carry-forward cap; entitlement override incl. negative balance; override RBAC; leave-year start month validation; next-leave-year charging |
| Work locations | `location/WorkLocationConfigIT` | CRUD RBAC; radius < 50 rejected; blank name / missing coordinates rejected; tenant-scoped; 401s |
| Notifications | `notification/NotificationIT`, `notification/NotificationFlowIT` | List + mark all read; approving leave notifies the requester; push-token register is idempotent; 401 |
| Overtime | `overtime/OvertimeFlowIT` | Submit → routed → decided; employee cannot read the approvals queue |
| Payroll maths | `payroll/PayrollCalculatorTest`, `payroll/CompensationServiceTest` | EPF/SOCSO/EIS/PCB to the sen; EPF rounds up; employer 12 % above RM 5,000; SOCSO/EIS ceiling; OT + claims excluded from statutory; daily/hourly derivation; unpaid full / half / hourly deductions; paid leave never deducts; weekends not counted |
| Payroll run | `payroll/PayrollRunIT`, `payroll/CompLeavePayrollIT`, `payroll/PartialLeavePayrollIT` | Run → approve → mark paid; not-generated view; manager + employee 403; 401; unpaid leave reduces basic, paid does not; partial unpaid leave reduces by the exact fraction |
| Exports | `payroll/export/PayrollExportIT`, `payroll/export/Cp39Test`, `payroll/export/CsvUtilTest` | Summary totals = sum of rows; each file has content + filename + MIME; unknown type 400; no run 400; manager 403; 401; CP39 fixed layout + sen conversion; CSV quoting |
| Schedule | `schedule/ScheduleIT` | Assign then week shows the shift; employee 403 |

**Not covered by automation (manual QA is the only safety net):** every screen's
layout and wording, navigation, permission prompts, camera, GPS, share sheet,
push delivery, the payslip PDF, token refresh on the device, offline behaviour,
the receipt camera and photo viewer, and the claims list totals / claim decisions
(`ClaimReceiptIT` covers only the receipt photo).

### 0.7 Case format

```
#### ID — Title
- [ ] Pass
- Account:  who to sign in as
- Pre:      state needed before step 1
- Steps:    the tap flow
- Expect:   the result that must be true
```

Sign-off block (copy per pass):

```
Build / update id: ____________   Backend commit: ____________
Device(s): ____________________   Tester: __________  Date: __________
Pass type: Smoke / Full           Result: PASS / FAIL (list IDs)
```

---

## A. Authentication & onboarding

### A.1 Launch & onboarding (`app/index.tsx`, `app/(auth)/onboarding.tsx`)

#### AUTH-01 — Cold launch signed out shows onboarding
- [ ] Pass
- Account: none (fresh install or after Log out)
- Pre: no stored session
- Steps: 1. Launch the app.
- Expect: a brief coral spinner on a cream background, then the onboarding screen: headline "Your whole workday, in one warm place.", three feature rows ("Clock in with a glance", "Time off in a tap", "Payday, crystal clear"), a **Get started** button and an "I already have an account" link.

#### AUTH-02 — Wordmark is "Teamora" on every pre-login screen
- [ ] Pass
- Account: none
- Pre: signed out
- Steps: 1. Read the brand row on Onboarding. 2. Tap **I already have an account** and read the brand row on Login. 3. Tap **Create an account** and read the title of the setup wizard's first screen ("Welcome to Teamora"). 4. Read the legal line at the bottom of Login.
- Expect: the wordmark reads **Teamora** on all three screens. The word "lumi" / "Lumi" never appears as the product name (including the "Terms & Privacy" line).

#### AUTH-03 — Onboarding navigation: Skip, Get started, I already have an account
- [ ] Pass
- Account: none
- Pre: on Onboarding
- Steps: 1. Tap **Skip** → then **Back**. 2. Tap **Get started** → then × (top left). 3. Tap **I already have an account**.
- Expect: **Skip** and **I already have an account** open the Login screen; **Get started** opens the new-company setup wizard ("Welcome to Teamora"). Back / × return to Onboarding.

#### AUTH-04 — Onboarding makes no false promise
- [ ] Pass
- Account: none
- Pre: on Onboarding
- Steps: 1. Read the feature descriptions and look at the hero area.
- Expect: copy matches what the app does (a selfie + GPS check, not biometric face recognition). No visible grey "placeholder" box labelled as an illustration in a release build.

### A.2 Login (`app/(auth)/login.tsx`)

#### AUTH-05 — Employee signs in and lands in the staff app
- [ ] Pass
- Account: `amir@lumi.com`
- Pre: on Login
- Steps: 1. Type the email in **Work email**. 2. Type `password` in **Password**. 3. Tap **Sign in**.
- Expect: button reads "Signing in…" and is disabled while pending; then staff **Home** opens with the five-tab pill bar (Home · Attendance · Calendar · Payroll · Profile). Greeting shows "Amir 👋".

#### AUTH-06 — HR admin signs in and lands in the admin app
- [ ] Pass
- Account: `sarah@lumi.com`
- Pre: on Login
- Steps: 1. Sign in.
- Expect: admin **Dashboard** opens ("Good morning/afternoon/evening, Sarah", subtitle "<day>, <date> · HR Admin") with tabs Home · Staff · Approve · Payroll · Profile.

#### AUTH-07 — Owner lands in the admin app
- [ ] Pass
- Account: `owner@lumi.com`
- Pre: on Login
- Steps: 1. Sign in.
- Expect: admin Dashboard; subtitle ends "· Owner".

#### AUTH-08 — Manager lands in the staff app with an Approvals card
- [ ] Pass
- Account: `nadia@lumi.com`
- Pre: on Login
- Steps: 1. Sign in.
- Expect: staff Home (not the admin dashboard) with an **Approvals** card ("Review your team's requests") under the attendance hero.

#### AUTH-09 — Empty fields are caught before any request
- [ ] Pass
- Account: none
- Pre: on Login, both fields empty
- Steps: 1. Tap **Sign in**. 2. Enter an email only and tap **Sign in**.
- Expect: both times the red line "Enter your email and password." appears; the button never shows "Signing in…".

#### AUTH-10 — Wrong password shows an error and stays on Login
- [ ] Pass
- Account: `amir@lumi.com` with password `wrong`
- Pre: on Login
- Steps: 1. Sign in.
- Expect: a red error message under the fields (server message, 401), an error haptic, the button returns to **Sign in**, and the typed email is kept.

#### AUTH-11 — Unknown email is rejected the same way
- [ ] Pass
- Account: `nobody@lumi.com` / `password`
- Pre: on Login
- Steps: 1. Sign in.
- Expect: same error treatment as AUTH-10; the message does not reveal whether the email exists.

#### AUTH-12 — Email is trimmed and case-insensitive
- [ ] Pass
- Account: type `  Amir@Lumi.com  ` (spaces + capitals)
- Pre: on Login
- Steps: 1. Sign in with `password`.
- Expect: sign-in succeeds.

#### AUTH-13 — Server unreachable shows a connection message
- [ ] Pass
- Account: any
- Pre: stop the API (`docker compose stop api`) or enable Airplane mode
- Steps: 1. Sign in.
- Expect: "Could not sign in. Check your connection." — no crash, no endless spinner. Restart the API afterwards.

#### AUTH-14 — Demo credentials only in non-production builds
- [ ] Pass
- Account: none
- Pre: one dev / preview build and one production build
- Steps: 1. Open Login on the dev (Metro) or preview build. 2. Open Login on the production build.
- Expect: dev / preview shows the helper line "Demo — staff: amir@lumi.com · admin: sarah@lumi.com (password: password)."; the **production build shows no demo credentials at all**.

#### AUTH-15 — "Forgot password?" explains how to get a reset
- [ ] Pass
- Account: none
- Pre: on Login
- Steps: 1. Tap the coral **Forgot password?** link under the Password field. 2. Dismiss the alert. 3. Check the fields.
- Expect: a system alert titled "Forgot your password?" with the message "Ask your HR admin to reset your password. They can set a new one from your employee page." and an OK button. Nothing is sent, no email is promised, and no other screen opens — there is **no** self-service / email reset; the real path is an HR admin reset (ADM-EMP-36). Any email already typed is still there after dismissing. A link that does nothing is a fail.

#### AUTH-16 — Password field is masked; Back returns to Onboarding
- [ ] Pass
- Account: none
- Pre: on Login
- Steps: 1. Type in **Password**. 2. Tap **Back**.
- Expect: characters are masked; no auto-capitalise / auto-correct on either field; Back returns to Onboarding.

### A.3 New-company setup wizard (`app/(auth)/setup.tsx`, `app/admin/setup.tsx`, `src/features/setup/`, `src/components/wizard/`)

Run AUTH-17 → SETUP-09 in order on a clean install; they walk the wizard once, start to finish.
Throughout, watch the motion: each step drifts in from the right (Back: from the left), the icon
pops, title → subtitle → body follow ~70 ms apart, the progress bar springs. Nothing should jump,
flash white, or overlap the old step for more than a blink.

#### AUTH-17 — The wizard creates a company, signs in as its owner, and keeps going
- [ ] Pass
- Account: new — company `QA Bakery`, name `Qa Owner`, email `qa+<timestamp>@example.com`, password `password`
- Pre: signed out, on Onboarding
- Steps: 1. Tap **Get started**. 2. On "Welcome to Teamora" tap **Get started**. 3. "First, about you": fill **Full name**, **Work email**, **Password** → **Continue**. 4. "Your company": fill **Company name**, leave SSM empty → **Create company**.
- Expect: the button shows a spinner, then the wizard moves on to **"Your work week"** — it does **not** jump to the Dashboard. The header shows a close (×) button instead of Back: steps 1–3 can't be revisited. Server: `POST /api/auth/login` with the new email works; `GET /api/companies/me` shows `setupCompletedAt: null`.

#### AUTH-18 — Empty / invalid fields are caught on the step, in place
- [ ] Pass
- Account: none
- Pre: on "First, about you"
- Steps: 1. Tap **Continue** with everything empty. 2. Enter name, email `not-an-email`, password `short` → **Continue**. 3. Fix them → **Continue**; on "Your company" tap **Create company** with the name empty.
- Expect: 1. "Enter your name." / "Enter your work email." / "Use at least 8 characters." under the fields. 2. "That doesn't look like an email address." and the password rule. 3. "Enter your company name." No request is sent in any of these; an error clears as soon as its field is edited.

#### AUTH-19 — Duplicate email sends you back to the email field
- [ ] Pass
- Account: use `amir@lumi.com`
- Pre: on "Your company" with an existing email entered on the previous step
- Steps: 1. Tap **Create company**.
- Expect: the wizard slides **back** to "First, about you" with "An account with this email already exists. Use a different email, or sign in instead." under **Work email**; name and password are kept. Changing the email and continuing creates the company.

#### AUTH-20 — Leaving before the account exists
- [ ] Pass
- Account: none
- Pre: on "Welcome to Teamora"
- Steps: 1. Tap **Sign in** in "Already use Teamora? Sign in". 2. Back on Login tap **Create an account**, go to "Your company", tap Back twice, then × on Welcome.
- Expect: 1. Login opens. 2. Back walks step by step (typed values kept); × returns to the previous screen. The iOS edge-swipe does **not** pop the wizard. `/register` (old link) redirects to the wizard.

#### SETUP-01 — Work week saves and prefills
- [ ] Pass
- Account: the owner from AUTH-17
- Pre: on "Your work week"
- Steps: 1. Confirm Mon–Fri, **8 h**, **9:00 AM**, **5 min** are prefilled. 2. Untick every day. 3. Tick Mon–Sat, set hours to 8.5 (tap +), hold **+** on "Late after" to run it up, set 15 → **Continue**.
- Expect: 2. "Pick at least one working day." and Continue disabled. 3. Stepper numbers pop, stop at min/max (buttons dim), holding repeats and releasing adds no extra step. Server: `GET /api/admin/company-settings` → `defaultWorkingDays: 63`, `defaultHoursPerDay: 8.5`, `workStartTime: "09:00"`, `lateGraceMinutes: 15`.

#### SETUP-02 — Leave entitlements
- [ ] Pass
- Account: same
- Pre: on "Leave your team gets"
- Steps: 1. Read the list. 2. Set Annual Leave to 12 → **Continue**. 3. Tap Back, then Back again, then forward twice.
- Expect: 1. Annual 16, Medical 14, Emergency 5 (each "Paid · days a year"); the note names the Employment Act minimums and says "Unpaid Leave has no yearly limit." 2. Server: `GET /api/admin/leave/types` → Annual `defaultEntitlementDays: 12`, others unchanged. 3. Values are kept in both directions; from Leave, Back reaches Work week but never the account steps.

#### SETUP-03 — Public holidays (suggested, never automatic)
- [ ] Pass
- Account: same
- Pre: on "Public holidays" (run in Oct–Dec to see both groups)
- Steps: 1. Read the list. 2. Untick one nationwide holiday, tick one state-specific one. 3. Tap **Add N holidays**. 4. Tap Back from the next step.
- Expect: 1. "REST OF <year>" shows only dates from today on, then "<next year>"; nationwide rows are ticked, state-specific rows are unticked with their note. The button counts the ticks. 3. Moves on; Company calendar shows exactly the ticked holidays. 4. Those rows now show an "Added" chip and can't be ticked. **Skip** instead adds nothing.

#### SETUP-04 — Workplace: one site (real phone for GPS)
- [ ] Pass
- Account: same
- Pre: on "Where does your team clock in?"
- Steps: 1. Continue is disabled until a tile is chosen. 2. Tap **One office or site** → **Continue** with nothing filled. 3. Type a site name, tap **Use my current location** (allow), set radius to 300 → **Continue**.
- Expect: 2. "Give the site a name." and the location card outlined red with its message. 3. "Location captured" with coordinates; the line reads "Staff must be within 300 m to clock in."; the name field stays visible above the keyboard and the button rides above it. Server: `GET /api/admin/work-locations` → one active site, `radiusM: 300`. Denying the permission shows "Location permission is off…".

#### SETUP-05 — Workplace: anywhere / changing your mind
- [ ] Pass
- Account: same
- Pre: SETUP-04 done, on "Invite your team"
- Steps: 1. Tap Back, choose **Anywhere** → **Continue**. 2. Back again, choose **One office or site** → **Continue**.
- Expect: 1. the site is switched to inactive (not duplicated). 2. the same site is active again — still exactly one row in Work locations.

#### SETUP-06 — Invite your team and share logins
- [ ] Pass
- Account: same
- Pre: on "Invite your team"
- Steps: 1. Enter a name with email `bad`, tap **Add another**, enter a valid person as Manager → **Add 2 people**. 2. Fix the email → **Add 2 people**. 3. Add one with `amir@lumi.com`. 4. Tap **Share login** on a created person. 5. **Continue**.
- Expect: 1. "Enter a valid work email." on that card; nothing is created. 2. Both cards turn into summaries with a tick, role · email, a readable **Temporary password** (like `Kopi-4821-teh`) and **Share login**; the button now reads **Continue**. 3. That card shows "An account with this email already exists" and can be removed with ×. 4. The share sheet text names the person, the company, the email and the temporary password, and ends "You can change it in Profile → Change password." 5. Staff tab later lists them; each can sign in with the shared password; with a site set in SETUP-04 they are assigned to it.

#### SETUP-07 — Finale
- [ ] Pass
- Account: same
- Pre: on the last step
- Steps: 1. Watch it arrive. 2. Read the checklist. 3. Tap **Go to dashboard**.
- Expect: 1. a coral circle springs in, its tick draws, a soft burst of dots and rings fades out, a success haptic, then "You're all set" and the rows appear one by one. 2. Rows match what was really done ("12 added", the site name or "Anywhere", "2 invited"; skipped steps say "Skipped" with a dash). 3. Dashboard opens with **no** "Finish setting up" card; `GET /api/companies/me` → `setupCompletedAt` set. On a real device the notification-permission prompt appears now, not mid-wizard.

#### SETUP-08 — Finish later, then resume from the dashboard
- [ ] Pass
- Account: a second new owner (repeat AUTH-17)
- Pre: on "Your work week" right after creating the company
- Steps: 1. Tap × → **Keep going**; × again → **Finish later**. 2. On the Dashboard tap **Continue setup**. 3. Tap × on the first step. 4. Force-quit, reopen. 5. Finish the wizard.
- Expect: 1. the alert explains progress is saved; Finish later opens the Dashboard with the "Finish setting up your company" card on top. 2. the wizard opens at **Your work week** (no welcome/account steps) with saved values prefilled. 3. back on the Dashboard, card still there. 4. still signed in, card still there. 5. card gone for good. An HR Admin of the same company never sees the card.

#### SETUP-09 — Errors and Reduce Motion
- [ ] Pass
- Account: any owner mid-wizard
- Pre: —
- Steps: 1. Stop the API (or go offline) and tap **Continue** on Work week. 2. Restore, tap again. 3. Settings → Accessibility → Motion → **Reduce Motion** on, reopen the wizard and step through to the finale.
- Expect: 1. a red note above the button: "Can't reach Teamora. Check your connection and try again."; the step stays, nothing is lost. 2. it proceeds. 3. steps crossfade with no sliding or scaling; the finale fades in with no burst.

#### AUTH-21 — A new company starts with the default leave catalogue and is isolated
- [ ] Pass
- Account: the owner created in AUTH-17
- Pre: AUTH-17 done
- Steps: 1. Profile tab → **Company settings** → scroll to **Leave types**. 2. Staff tab.
- Expect: four leave types (Annual 16, Medical 14, Emergency 5, Unpaid 0 with an "Unpaid" chip). Staff list shows exactly **1 employee** (yourself) — no Lumi or Nusantara people.

### A.4 Session lifecycle

#### AUTH-22 — Session is restored on relaunch
- [ ] Pass
- Account: `amir@lumi.com`
- Pre: signed in
- Steps: 1. Force-quit the app. 2. Relaunch.
- Expect: spinner, then straight to staff Home without the login screen.

#### AUTH-23 — Log out (staff)
- [ ] Pass
- Account: `amir@lumi.com`
- Pre: signed in
- Steps: 1. Profile tab → **Log out**. 2. Force-quit and relaunch.
- Expect: Onboarding appears immediately; after relaunch the app is still signed out. Swiping / hardware back does not return to a signed-in screen.

#### AUTH-24 — Log out (admin)
- [ ] Pass
- Account: `sarah@lumi.com`
- Pre: signed in
- Steps: 1. Profile tab → **Log out**.
- Expect: Onboarding; same guarantees as AUTH-23.

#### AUTH-25 — Switching accounts never shows the previous user's data
- [ ] Pass
- Account: `amir@lumi.com` then `arjun@lumi.com`
- Pre: signed in as Amir; open Home, Leave, Payroll and Profile so data is cached
- Steps: 1. Log out. 2. Immediately sign in as Arjun. 3. Open Home, Leave, Payroll, Profile.
- Expect: every screen shows **Arjun's** name, balances and payslip from the first frame — never Amir's name, initial or numbers, not even briefly.

### A.5 Change my password (`src/components/account/ChangePasswordScreen.tsx`, `app/(staff)/change-password.tsx`, `app/admin/change-password.tsx`)

One shared screen, reached from both Profile tabs. These cases change a seeded
account's password — use **Arjun** (and Sarah where stated), and afterwards change
it back to `password` (allowed: it is 8 characters and differs from the current
one) or reset the data (0.5), otherwise later cases cannot sign in. Test password
used below: `Teamora-qa-1`. Admin-side reset is under D.7 (ADM-EMP-35 → 40).

#### AUTH-26 — Change password is reachable from both apps
- [ ] Pass
- Account: `arjun@lumi.com`, then `sarah@lumi.com`
- Pre: signed in
- Steps: 1. Staff app: Profile → first menu card → **Change password**. 2. Tap the back chevron. 3. Admin app: Profile → **Change password** (last row). 4. Back.
- Expect: both open the same screen titled "Change password" with three masked fields — **Current password**, **New password** (placeholder "At least 8 characters"), **Confirm new password** — a "Show passwords" checkbox, a **Change password** button and the hint "Forgotten your current password? Ask your HR admin to reset it for you." Back returns to the Profile tab it was opened from; no tab bar on the screen.

#### AUTH-27 — Change password (happy path): new one works, old one does not
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password; current password is `password`
- Steps: 1. **Current password**: `password`. 2. **New password**: `Teamora-qa-1`. 3. **Confirm new password**: `Teamora-qa-1`. 4. Tap **Change password**. 5. Dismiss the alert. 6. Profile → **Log out**. 7. Sign in with `password`. 8. Sign in with `Teamora-qa-1`.
- Expect: the button reads "Saving…" and is disabled while pending; an alert "Password changed" — "Use your new password the next time you sign in. You've been signed out on your other devices."; the screen closes back to Profile and Arjun is **still signed in** on this device. Step 7 fails with the normal wrong-password error (AUTH-10); step 8 signs in.

#### AUTH-28 — Wrong current password
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password
- Steps: 1. **Current password**: `not-my-password`. 2. New + confirm: `Teamora-qa-2`. 3. Tap **Change password**. 4. Log out and sign in with the password that was valid before this case.
- Expect: "Saving…", then the red message "Current password is incorrect" **under the Current password field** (not at the bottom of the form); the screen stays open with all three fields kept; no "Password changed" alert. Step 4 signs in — the password was not changed and the session was not ended.

#### AUTH-29 — New password rules are caught before any request
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password, all fields empty
- Steps: 1. Tap **Change password** with everything blank. 2. Correct current password, new + confirm `short12` (7 characters). 3. New + confirm of 73 characters. 4. New + confirm identical to the current password.
- Expect: 1 → "Enter your current password." under Current and "Use at least 8 characters." under New; 2 → "Use at least 8 characters."; 3 → "Keep it to 72 characters or fewer."; 4 → "Choose a password different from your current one." Each message sits under its own field, the button never shows "Saving…", and the password is unchanged. Exactly 8 and exactly 72 characters are accepted.

#### AUTH-30 — Confirmation must match
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password
- Steps: 1. Correct current password. 2. **New password**: `Teamora-qa-3`. 3. **Confirm new password**: `Teamora-qa-4`. 4. Tap **Change password**. 5. Fix the confirmation and submit.
- Expect: step 4 → "This doesn't match your new password." under the Confirm field; nothing is sent and the password is unchanged (the old one still signs in). Step 5 succeeds as in AUTH-27. A confirmation that differs only by letter case or a trailing space is also a mismatch.

#### AUTH-31 — "Show passwords" toggle
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password with text in all three fields
- Steps: 1. Tap **Show passwords**. 2. Tap it again. 3. With VoiceOver / TalkBack on, focus the control.
- Expect: ticked → the box fills coral with a white tick and **all three** fields show plain text; unticked → all three are masked again; the typed values are not lost either way. The control is announced as a checkbox with its checked state. No auto-capitalisation on any of the fields.

#### AUTH-32 — This device stays signed in; every other device is signed out
- [ ] Pass
- Account: `arjun@lumi.com` on device A **and** device B
- Pre: both devices signed in and showing data
- Steps: 1. On A, change the password (AUTH-27 steps 1–5). 2. On A, open Leave and Claims, then force-quit and relaunch. 3. On B, keep using the app (open Leave, Claims, Payroll) for up to 30 minutes — or shorten the access-token lifetime as in X-SES-01. 4. On B, sign in with the old password, then the new one.
- Expect: A is never interrupted: data loads, and after relaunch the session is restored straight to Home. B is **not** signed out instantly — it keeps working until its access token expires (≤ 30 min, `access-token-ttl-minutes`), then the next request returns it to the signed-out flow (Onboarding / Login), not to a screen of error boxes (X-SES-02). On B the old password is rejected and the new one signs in. Log how long B stayed usable.

#### AUTH-33 — Save failure keeps the form
- [ ] Pass
- Account: `arjun@lumi.com`
- Pre: on Change password with valid values; then Airplane mode (or `docker compose stop api`)
- Steps: 1. Tap **Change password**. 2. Restore the connection and tap it again.
- Expect: offline → "Could not change your password. Check your connection and try again." in red above the button; all three fields keep their values; no alert, no navigation. Online → succeeds once (double-tapping the button does not send two requests).

---

## B. Staff app (EMPLOYEE — also applies to MANAGER)

Default account: **`amir@lumi.com`** unless a case says otherwise.

### B.1 Home (`app/(staff)/(tabs)/home.tsx`, `AttendanceHero`)

#### STF-HOME-01 — Header: greeting, name, avatar, collapse on scroll
- [ ] Pass
- Account: Amir
- Pre: signed in
- Steps: 1. Open Home. 2. Scroll the body up and down.
- Expect: eyebrow shows a sun icon + "Good morning" (before 12:00), "Good afternoon" (12:00–17:59) or "Good evening" (18:00+); title "Amir 👋"; avatar with initial and green online dot. The header stays pinned and the title / avatar shrink smoothly while scrolling; nothing clips.

#### STF-HOME-02 — Hero state 1: not clocked in
- [ ] Pass
- Account: an employee who has **not** clocked in today
- Pre: no attendance record today
- Steps: 1. Open Home.
- Expect: espresso hero with eyebrow "Today", headline "Not clocked in yet", the shift line, and a **Clock In** button. **No running timer and no "Currently working" label.**

#### STF-HOME-03 — Hero state 2: working, timer is real elapsed time
- [ ] Pass
- Account: Amir
- Pre: clocked in (STF-CLK-01), note the exact clock-in time
- Steps: 1. Open Home and compare the timer with (now − clock-in time). 2. Watch it for 10 s. 3. Background the app for 2 minutes, then return. 4. Force-quit and relaunch.
- Expect: eyebrow "Currently working" with a blinking dot and "In at h:mm AM/PM" matching the real clock-in time; big timer `h:mm:ss` equals the true elapsed time (± 2 s), ticks every second, **jumps forward correctly after backgrounding**, and is still correct after relaunch (never restarts from 0 and never shows a stale figure). Sub-line "Worked today · 9:00 AM – 6:00 PM". Button **Clock Out**.

#### STF-HOME-04 — Clock out from Home asks first, then shows "Done for today"
- [ ] Pass
- Account: Amir
- Pre: in the working state
- Steps: 1. Tap **Clock Out**. 2. In the dialog "Clock out now?" tap **Cancel**. 3. Tap **Clock Out** again and confirm **Clock out**.
- Expect: Cancel leaves the timer running. Confirming shows "Clocking out…" briefly, then the hero flips to state 3 (STF-HOME-05) **on the Home screen** — the app does not navigate to the Clock In screen.

#### STF-HOME-05 — Hero state 3: done for today (static total)
- [ ] Pass
- Account: Amir
- Pre: clocked out today
- Steps: 1. Open Home. 2. Wait 30 s. 3. Relaunch the app.
- Expect: eyebrow "Done for today" with "In at …"; total shown as `Xh Ym` equal to (clock-out − clock-in) in whole minutes; the figure **does not tick**; no Clock In / Clock Out button that would allow a second clock-in today.

#### STF-HOME-06 — Hero when on approved leave today
- [ ] Pass
- Account: an employee whose attendance status today is ON_LEAVE
- Pre: such a record exists (seed or DB)
- Steps: 1. Open Home.
- Expect: hero reads "On leave today" rather than "Not clocked in yet". *(Approving leave does not currently write an ON_LEAVE attendance row — see Known limitations; this state only appears where a record already exists.)*

#### STF-HOME-07 — Quick actions navigate
- [ ] Pass
- Account: Amir
- Pre: on Home
- Steps: 1. Tap **Leave** → back. 2. Tap **Claim** → back. 3. Tap **Schedule**. 4. Return to Home, tap **Payroll**.
- Expect: Leave → "Leave" screen; Claim → "Claims" screen; Schedule → Calendar tab; Payroll → Payslip tab. Each tile gives light feedback on tap.

#### STF-HOME-08 — Today's summary shows real numbers
- [ ] Pass
- Account: Amir
- Pre: note Amir's Annual Leave "remaining" on the Leave screen and "PENDING APPROVAL" total on Claims
- Steps: 1. Read the three tiles under "Today's summary".
- Expect: **Attendance** = "—"/"Not in" before clock-in, "On time" if clocked in by 09:05, "Late" after 09:05 (Malaysia time); **Annual Leave** = the same remaining figure as the Leave screen with a "d" suffix (fractions like 11.5d must render cleanly, not 11.500000001d); **Claims pending** = the same RM total as the Claims screen. Text never wraps or truncates mid-number.

#### STF-HOME-09 — Employee has no Approvals card
- [ ] Pass
- Account: Amir (EMPLOYEE)
- Pre: on Home
- Steps: 1. Look between the hero and the quick actions.
- Expect: no "Approvals" card.

#### STF-HOME-10 — Home loading and error states
- [ ] Pass
- Account: Amir
- Pre: stop the API, then open the app with a stored session — or toggle Airplane mode and pull the app to foreground after 30 s
- Steps: 1. Open Home. 2. Restore the connection and tap **Try again**.
- Expect: the hero area shows "Couldn't load this. Check your connection." with a **Try again** button; tapping it after the connection returns loads the hero. Summary tiles show "—", not zeros that look real.

### B.2 Clock In (`app/(staff)/clock-in.tsx`)

#### STF-CLK-01 — Clock in, unassigned employee (no geofence)
- [ ] Pass
- Account: an employee with **no** work location assigned and not yet clocked in today
- Pre: Home shows "Not clocked in yet"
- Steps: 1. Tap **Clock In**. 2. Check the dark screen: title "Clock In", current time + date, round viewfinder, location card. 3. Tap **Verify & Clock In**.
- Expect: location card shows the employee's location / company name with subtitle "Your work location"; **no location permission prompt**; the button cycles through "Taking photo…" (if camera on) → "Verifying…"; the screen closes back to Home, which now shows the working state with a timer starting near 0:00:00.

#### STF-CLK-02 — Time and date on the clock-in screen are live and correct
- [ ] Pass
- Account: any staff
- Pre: on Clock In
- Steps: 1. Compare the time with the device clock. 2. Wait until the minute changes.
- Expect: 12-hour "h:mm AM/PM" and "Weekday, D Month" match the device; the minute updates within ~10 s of rolling over.

#### STF-CLK-03 — Back chevron leaves without clocking in
- [ ] Pass
- Account: any staff, not clocked in
- Pre: on Clock In
- Steps: 1. Tap the chevron at top-left.
- Expect: returns to Home, still "Not clocked in yet"; no attendance row created (Attendance tab unchanged).

#### STF-CLK-04 — Camera permission denied → clock in without a photo
- [ ] Pass
- Account: any staff, not clocked in (physical device; or simulator, which has no camera)
- Pre: camera permission denied for the app
- Steps: 1. Open Clock In. 2. Tap **Verify & Clock In**.
- Expect: viewfinder shows the face icon on a dark disc with a coral dashed ring and the note "Camera is off — you'll clock in without a photo."; clock-in **succeeds**; on the admin Live Attendance board this person shows an initial avatar, not a photo.

#### STF-CLK-05 — Assigned site, inside the radius
- [ ] Pass
- Account: an employee assigned to a work location (ADM-EMP-12)
- Pre: device (or simulated) location within the site radius; location permission not yet decided
- Steps: 1. Open Clock In. 2. Tap **Verify & Clock In**. 3. Allow location when prompted.
- Expect: card shows the **site name** with "Verify you are on-site to clock in"; button shows "Checking location…" then "Verifying…"; clock-in succeeds and Home shows the working state.

#### STF-CLK-06 — Assigned site, outside the radius
- [ ] Pass
- Account: same assigned employee, not clocked in
- Pre: location well outside the radius (simulator: Features → Location → a far city)
- Steps: 1. Tap **Verify & Clock In**.
- Expect: amber message "You're ~<N> m from <site name>. Move within <radius> m to clock in."; the button re-enables; **no** attendance record is created (Home still "Not clocked in yet").

#### STF-CLK-07 — Assigned site, location permission denied
- [ ] Pass
- Account: assigned employee, not clocked in
- Pre: location permission denied for the app
- Steps: 1. Tap **Verify & Clock In**.
- Expect: "Location is needed to clock in at your work site. Enable location for Teamora in Settings, then try again." No clock-in. After enabling location in Settings and retrying, it succeeds (inside the radius).

#### STF-CLK-08 — Assigned site, no GPS fix
- [ ] Pass
- Account: assigned employee
- Pre: location permission granted but no fix (simulator: Location → None; device: Airplane mode with Wi-Fi off, indoors)
- Steps: 1. Tap **Verify & Clock In** and wait up to ~12 s.
- Expect: "Could not get your location. Move to an open area and try again." The button re-enables; no hang beyond ~12 s.

#### STF-CLK-09 — Site set to Inactive → no geofence
- [ ] Pass
- Account: assigned employee, not clocked in
- Pre: admin sets the employee's site to **Inactive** (ADM-LOC-07); device far from the site
- Steps: 1. Staff re-opens the app. 2. Clock in.
- Expect: clock-in succeeds from anywhere (an inactive site does not enforce a radius).

#### STF-CLK-10 — Late vs on-time status
- [ ] Pass
- Account: two employees
- Pre: one clocks in at or before 09:05 Malaysia time, the other after 09:05
- Steps: 1. Each clocks in. 2. Open Attendance tab and Home summary.
- Expect: before / at 09:05 → "On time" on Home and a present-style chip in Attendance; after 09:05 → "Late" in both places and counted under **Late** in the stats row.

#### STF-CLK-11 — Double clock-in is blocked
- [ ] Pass
- Account: Amir on two devices (or one device + Swagger)
- Pre: device A already clocked in; device B still showing the Clock In screen from before
- Steps: 1. On device B tap **Verify & Clock In**.
- Expect: amber message "Already clocked in today"; the existing clock-in time is unchanged.

#### STF-CLK-12 — Clock-out before clock-in is impossible
- [ ] Pass
- Account: an employee not clocked in
- Pre: Home in state 1
- Steps: 1. Confirm no **Clock Out** control exists anywhere. 2. (Optional, Swagger) `POST /api/attendance/clock-out`.
- Expect: the UI offers only **Clock In**. The API answers 400 "Not clocked in today".

#### STF-CLK-13 — Double clock-out is blocked
- [ ] Pass
- Account: Amir on two devices
- Pre: both showing the working state; clock out on device A
- Steps: 1. On device B tap **Clock Out** and confirm.
- Expect: a friendly failure ("Already clocked out today") and the hero refreshes to "Done for today" with the total from device A — the worked total is not overwritten.

#### STF-CLK-14 — Network failure during clock-in
- [ ] Pass
- Account: unassigned employee, not clocked in
- Pre: enable Airplane mode after opening the Clock In screen
- Steps: 1. Tap **Verify & Clock In**. 2. Restore the network and tap again.
- Expect: "Could not clock in. Please try again."; the button re-enables; the retry succeeds once and only once (one attendance row).

### B.3 Attendance (`app/(staff)/(tabs)/attendance.tsx`)

#### STF-ATT-01 — Stats row and month chip
- [ ] Pass
- Account: Amir
- Pre: at least one clock-in this month
- Steps: 1. Open the Attendance tab.
- Expect: title "Attendance"; the chip shows the current month in words (e.g. "October 2026"); four tiles **Present / Late / Leave / OT** whose counts match the day list below (Present counts on-time + remote days, Late counts late days, OT is time worked beyond 9 h per day).

#### STF-ATT-02 — "This week" chart
- [ ] Pass
- Account: Amir
- Pre: clocked in and out at least once this week
- Steps: 1. Read the bar chart and the "<total> total" label.
- Expect: seven bars labelled M T W T F S S; days with a completed clock-out are filled coral, days with no hours are empty; the total equals the sum of completed days (a day still in progress contributes 0 until clock-out).

#### STF-ATT-03 — Day list rows
- [ ] Pass
- Account: Amir
- Pre: one completed day and one day in progress
- Steps: 1. Read each row.
- Expect: date + weekday on the left; status chip (the in-progress day shows a live dot); "h:mm → h:mm" times (only the in time while still working); worked label on the right. Times match what Home showed. Rows are divided by hairlines.

#### STF-ATT-04 — Empty month
- [ ] Pass
- Account: a brand-new employee (never clocked in)
- Pre: none
- Steps: 1. Open Attendance.
- Expect: stats all 0 / "0h", chart all empty, and the empty state "No attendance yet — Your clock-in history for this month will appear here." **Log overtime** is still available.

#### STF-ATT-05 — Month chip behaviour
- [ ] Pass
- Account: Amir
- Pre: on Attendance
- Steps: 1. Tap the month chip (it shows a down-chevron).
- Expect: either a month picker opens and the screen reloads for the chosen month, or the chip has no chevron. A chevron that does nothing is a fail.

#### STF-ATT-06 — Log overtime entry point
- [ ] Pass
- Account: Amir
- Pre: on Attendance
- Steps: 1. Tap **Log overtime**.
- Expect: "Log overtime" form slides up (continues in B.6).

### B.4 Leave (`app/(staff)/leave.tsx`, `leave-apply.tsx`)

#### STF-LVE-01 — Balance tiles
- [ ] Pass
- Account: Amir (fresh reset: Annual 12 / 16, Medical 8 / 14, Emergency 3 / 5)
- Pre: data reset
- Steps: 1. Home → **Leave**.
- Expect: header "Leave" with subtitle "<n> pending approval" (or "Up to date"). One tile per **tracked** leave type showing remaining (big, in the type's colour) "/entitled", the type name, and a progress bar = used ÷ (entitled + carried). Figures: 12/16, 8/14, 3/5.

#### STF-LVE-02 — Tiles never break a word; untracked types have no tile
- [ ] Pass
- Account: Amir on the narrowest supported phone (e.g. iPhone SE / 360 dp Android)
- Pre: default four leave types
- Steps: 1. Open Leave and read every tile label.
- Expect: "Annual Leave", "Medical Leave", "Emergency Leave" wrap **between words only** (never "Emergenc-y" or a clipped last letter); numbers stay on one line. **Unpaid Leave (accrual None) shows no balance tile at all** — no "0/0" tile.

#### STF-LVE-03 — Add a long-named and a fifth leave type
- [ ] Pass
- Account: Sarah then Amir
- Pre: Sarah creates leave type "Compassionate Bereavement Leave" (paid, 3 days, Fixed annual) — ADM-LT-01
- Steps: 1. Amir opens Leave.
- Expect: the new tile appears; with five tracked types the row still fits or wraps to a second row — no horizontal overflow, no mid-word break, no overlapping text.

#### STF-LVE-04 — Sub-lines: accrued so far / carried over
- [ ] Pass
- Account: Sarah then Amir
- Pre: Sarah sets Annual Leave to **Monthly accrual** (ADM-LT-05)
- Steps: 1. Amir opens Leave.
- Expect: the Annual tile gains a small line "<x> accrued so far" (x = 16 × elapsed months ÷ 12, 2 dp max) and the big number becomes accrued + carried − used. Fixed-annual types with nothing carried show **no** sub-line. Restore Annual to Fixed annual afterwards.

#### STF-LVE-05 — Apply full-day leave (happy path)
- [ ] Pass
- Account: Amir
- Pre: on Leave
- Steps: 1. Tap **Apply for leave**. 2. **Leave type**: Annual Leave. 3. **Duration**: Full day. 4. **Start date**: next Monday; **End date**: next Tuesday. 5. **Reason (optional)**: "QA trip". 6. Tap **Submit request**.
- Expect: button shows "Submitting…"; the form closes; "Your requests" gains a row "Annual Leave · <date range> · 2 days" with a **Pending** chip; subtitle count increases by 1. The balance tile is **unchanged** (balance is only deducted on approval).

#### STF-LVE-06 — Working-days-only counting across a weekend
- [ ] Pass
- Account: Amir (Mon–Fri schedule)
- Pre: none
- Steps: 1. Apply Full day, **Start** Friday, **End** the following Monday.
- Expect: request is accepted and listed as **2 days** (Saturday and Sunday are not counted).

#### STF-LVE-07 — Half day (AM / PM)
- [ ] Pass
- Account: Amir
- Pre: on Apply for leave
- Steps: 1. **Duration**: Half day. 2. Confirm the form now shows a single **Date** field (no End date) and "Which half?" with **Morning (AM)** / **Afternoon (PM)**. 3. Pick a working day and **Afternoon (PM)**. 4. Submit.
- Expect: request listed with a half-day duration label mentioning PM; after approval (MGR-04) the balance drops by exactly **0.5** and shows as e.g. "11.5".

#### STF-LVE-08 — Half day across two dates cannot be created
- [ ] Pass
- Account: Amir
- Pre: on Apply for leave with Full day selected and a 3-day range chosen
- Steps: 1. Switch **Duration** to Half day.
- Expect: the End date field disappears and the request is for the single **Date** only — the UI gives no way to submit a half day over a range. (API rule, automated: "A half-day leave covers one date only — set the same start and end date".)

#### STF-LVE-09 — Hourly leave with live hint
- [ ] Pass
- Account: Amir (8 hours per day)
- Pre: on Apply for leave
- Steps: 1. **Duration**: Hours. 2. Read the hint before typing. 3. Type `2` in **Hours**. 4. Optionally set **Start time (optional)** to 2:00 PM, then clear it and set it again. 5. Submit.
- Expect: placeholder "e.g. 2 (max 8)"; hint first reads "A full work day here is 8 hours.", then "2 hours = 0.25 day of your balance (8 hours per day)."; the time picker opens and its clear control works; request is listed with an hours duration label; after approval the balance drops by **0.25**.

#### STF-LVE-10 — Hours greater than hours/day rejected
- [ ] Pass
- Account: Amir (8 h/day)
- Pre: Duration = Hours
- Steps: 1. Type `9` and submit. 2. Type `8` and submit.
- Expect: 9 → red message "That's longer than a work day — enter 8 hours or less." and nothing is sent; 8 → accepted (equals one full day's fraction, 1.0).

#### STF-LVE-11 — Hours zero, blank, negative or text rejected
- [ ] Pass
- Account: Amir
- Pre: Duration = Hours
- Steps: 1. Submit with Hours blank. 2. With `0`. 3. With `abc` (if the keyboard allows). 4. With `1,5` (comma decimal).
- Expect: cases 1–3 → "Enter how many hours you need (more than 0)."; `1,5` is understood as 1.5 hours and accepted.

#### STF-LVE-12 — Leave on a rest day rejected
- [ ] Pass
- Account: Amir (Mon–Fri)
- Pre: on Apply for leave
- Steps: 1. Duration **Half day**, Date = a Saturday. Submit. 2. Duration **Full day**, Start = End = a Sunday. Submit.
- Expect: half day → "<D MMM YYYY> isn't one of your working days"; full day → "There are no working days in that range — public holidays and rest days don't use up your leave". No request is created.

#### STF-LVE-13 — Leave on a public holiday rejected
- [ ] Pass
- Account: Sarah then Amir
- Pre: Sarah creates a **Holiday** on a coming weekday (ADM-CAL-02)
- Steps: 1. Amir applies **Half day** on that date. 2. Amir applies **Full day** from the day before to the day after the holiday.
- Expect: half day → "<D MMM YYYY> is a public holiday — you don't need to apply for leave". The full-day range is accepted and counts **2 days** (the holiday is skipped).

#### STF-LVE-14 — Dates: no past start, end follows start
- [ ] Pass
- Account: Amir
- Pre: on Apply for leave, Full day
- Steps: 1. Open **Start date** and try to pick yesterday. 2. Pick a start date later than the current end date. 3. Open **End date** and try to pick a date before the start.
- Expect: yesterday is not selectable; the end date automatically moves up to the new start; dates before the start cannot be picked.

#### STF-LVE-15 — Unpaid leave warning
- [ ] Pass
- Account: Amir
- Pre: on Apply for leave
- Steps: 1. Select **Unpaid Leave**. 2. Select Annual Leave again.
- Expect: an amber banner "This leave is unpaid — salary will be deducted for the time taken." appears only while an unpaid type is selected.

#### STF-LVE-16 — Reason is optional; long reason is handled
- [ ] Pass
- Account: Amir
- Pre: on Apply for leave
- Steps: 1. Submit a valid request with no reason. 2. Submit another with > 500 characters in Reason.
- Expect: no-reason request is accepted; the over-long reason shows a red validation message (limit 500) and the form stays open with input kept.

#### STF-LVE-17 — Rejected and approved requests show the right chip
- [ ] Pass
- Account: Amir
- Pre: one request approved and one declined by Nadia (MGR-03, MGR-05)
- Steps: 1. Open Leave.
- Expect: chips read Approved / Rejected (declined) / Pending with distinct colours; newest request is at the top; a declined request leaves the balance untouched.

#### STF-LVE-18 — Empty requests state
- [ ] Pass
- Account: a newly added employee
- Pre: no leave requests
- Steps: 1. Open Leave. 2. Tap the empty-state **Apply for leave** button.
- Expect: "No leave requests yet — When you apply for leave, your requests will show up here." with an action that opens the apply form.

#### STF-LVE-19 — Hidden leave type is not offered
- [ ] Pass
- Account: Sarah then Amir
- Pre: Sarah sets Emergency Leave to **Hidden** (ADM-LT-06)
- Steps: 1. Amir opens Apply for leave.
- Expect: Emergency Leave is not among the **Leave type** chips. Existing Emergency requests still show in "Your requests". Restore to Active afterwards.

#### STF-LVE-20 — Applying beyond the balance
- [ ] Pass
- Account: Amir (Emergency 3 left)
- Pre: none
- Steps: 1. Apply Emergency Leave, Full day, 5 working days. 2. Have it approved.
- Expect: **current behaviour** — the request is accepted and, once approved, the tile shows a negative remaining (e.g. "-2"). Verify the negative number renders cleanly and the progress bar does not overflow. (Blocking over-entitlement is not built — see Known limitations.)

### B.5 Claims (`app/(staff)/claims.tsx`, `claim-submit.tsx`, `src/components/claims/*`, `src/components/media/*`)

#### STF-CLM-01 — Claims overview
- [ ] Pass
- Account: Amir
- Pre: none
- Steps: 1. Home → **Claim**.
- Expect: header "Claims" with subtitle "RM <x> reimbursed this month"; cream card "PENDING APPROVAL · RM <total>"; **Add a claim** button; "Recent claims" list where each row shows title, date, "RM amount" and a status chip. The pending total equals the sum of rows with a Pending chip.

#### STF-CLM-02 — Submit a claim (happy path)
- [ ] Pass
- Account: Amir
- Pre: note the pending total
- Steps: 1. Tap **Add a claim**. 2. **Category**: Petrol. 3. **Title**: "QA petrol". 4. **Amount (RM)**: `85.50`. 5. **Date**: today. 6. Tap **Submit claim**.
- Expect: "Submitting…", form closes, the list gains "QA petrol · RM 85.50 · Pending" at the top, and the pending total rises by exactly 85.50. Home → "Claims pending" tile shows the same new total.

#### STF-CLM-03 — Required fields
- [ ] Pass
- Account: Amir
- Pre: on "New claim"
- Steps: 1. Tap **Submit claim** with everything blank. 2. Enter a title only and submit. 3. Enter amount `0`. 4. Enter amount `-5`.
- Expect: blank → both "Please enter a title." and "Enter an amount greater than 0." under their fields; title only → just the amount error; 0 and negative → amount error. Nothing is submitted.

#### STF-CLM-04 — Every category can be chosen
- [ ] Pass
- Account: Amir
- Pre: on "New claim"
- Steps: 1. Tap each chip: Travel, Petrol, Meal, Medical, Other. 2. Submit one claim as **Medical**.
- Expect: only one chip is selected at a time; the submitted claim is accepted.

#### STF-CLM-05 — Amount formatting
- [ ] Pass
- Account: Amir
- Pre: on "New claim"
- Steps: 1. Submit `1234.5`. 2. Submit `12.345`.
- Expect: listed as "RM 1,234.50"; the three-decimal value is stored as money (2 dp) and never displays as 12.345.

#### STF-CLM-06 — Receipt thumbnail honesty
- [ ] Pass
- Account: Amir
- Pre: at least one claim
- Steps: 1. Look at the left slot of each claim row and at the **Add a claim** button icon (a camera). 2. Tap **Add a claim**.
- Expect: a row shows a photo **only** when that claim really has a receipt (STF-CLM-15); every other row shows a neutral grey receipt-icon tile (STF-CLM-16). A striped placeholder labelled "rcpt", a stock picture, or a broken-image box is a fail. The camera icon is honest: the form it opens has an **Add receipt photo** step (STF-CLM-09).

#### STF-CLM-07 — Empty state
- [ ] Pass
- Account: a newly added employee
- Pre: no claims
- Steps: 1. Open Claims. 2. Tap the empty-state **Add a claim**.
- Expect: pending card shows "RM 0.00"; "No claims yet — Submit an expense claim and it'll show up here for tracking."; the action opens the form.

#### STF-CLM-08 — Decided claims update the totals
- [ ] Pass
- Account: Amir
- Pre: Nadia approves one claim and declines another (MGR-06)
- Steps: 1. Open Claims.
- Expect: chips change to Approved / Rejected; the pending total drops by both amounts; "reimbursed this month" rises only by the approved amount (if its date is this month).

**Receipt photos** (STF-CLM-09 → 20). One optional photo per claim, taken with
the **back camera** at submit time; stored on the server and served by
`GET /api/claims/{id}/receipt`. Cases marked *physical device* cannot be run in
the iOS Simulator (no camera) — to get a claim with a receipt there, create it
through Swagger (`POST /api/claims` with a `receiptBase64` JPEG).

#### STF-CLM-09 — Receipt field, default state
- [ ] Pass
- Account: Amir
- Pre: on "New claim" (any environment)
- Steps: 1. Scroll to the field under **Date**.
- Expect: label "Receipt photo (optional)"; a card with a coral camera tile, "Add receipt photo", the line "Helps your approver check the claim faster." and a chevron; **Submit claim** sits below it. Nothing suggests a receipt is already attached.

#### STF-CLM-10 — Take a receipt photo
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: on "New claim" with a category, title, amount and date already entered; camera permission not yet decided
- Steps: 1. Tap **Add receipt photo**. 2. Allow the camera when prompted. 3. Read the capture screen. 4. Point at a paper receipt and tap the round shutter.
- Expect: a full-screen **back**-camera view slides up (not the selfie camera) with a close (X) button top-left and the chip "Fit the whole receipt in the frame"; under the shutter the hint goes "Starting camera…" → "Tap to take the photo" (the shutter is dimmed until ready) → "Saving photo…" with a light haptic. The camera then closes and the field shows a 64 px thumbnail of the photo, a sage tick with "Receipt attached", and the links **Retake** (coral) and **Remove** (red). The category, title, amount and date typed earlier are untouched. (Log the wording of the OS permission prompt — it currently mentions only the clock-in photo.)

#### STF-CLM-11 — Preview the captured receipt before submitting
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: a receipt is attached on the form (STF-CLM-10)
- Steps: 1. Tap the thumbnail. 2. Tap anywhere on the photo. 3. Open it again and tap the X.
- Expect: a dark full-screen viewer titled "Receipt photo" with the caption "Tap anywhere to close"; the **whole** photo is shown (fitted, not cropped), upright, and the amount on the receipt is readable. Either tap closes it and returns to the form with every field and the receipt intact. Android hardware back also closes only the viewer.

#### STF-CLM-12 — Retake replaces the photo
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: a receipt is attached on the form
- Steps: 1. Tap **Retake** and photograph a visibly different receipt. 2. Tap **Retake** again, then close the camera with X without taking a photo.
- Expect: step 1 → the thumbnail (and the preview) now show the **new** photo; there is still exactly one receipt. Step 2 → the camera closes and the previous photo is **kept** — cancelling a retake never clears the receipt.

#### STF-CLM-13 — Remove the receipt
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: a receipt is attached on the form
- Steps: 1. Tap **Remove**. 2. Tap **Submit claim**.
- Expect: the field returns to the "Add receipt photo" card (no thumbnail, no leftover note); the claim is submitted **without** a receipt and its list row shows the neutral tile (STF-CLM-16).

#### STF-CLM-14 — Cancel the camera without taking a photo
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: on "New claim" with fields filled, no receipt yet
- Steps: 1. Tap **Add receipt photo**. 2. Tap the X (on Android also try the hardware back button).
- Expect: back on the form with no receipt, no error note, and every typed value still there. The form itself is not closed.

#### STF-CLM-15 — Submit a claim with a receipt → thumbnail in the list
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: on "New claim": Meal, "QA lunch receipt", `23.40`, today, receipt attached; note the pending total
- Steps: 1. Tap **Submit claim**. 2. While it reads "Submitting…", tap **Retake** / **Remove**. 3. Read the new row on Claims.
- Expect: "Submitting…" with the button disabled; Retake / Remove do nothing while it is pending; the form closes. The new row "QA lunch receipt · RM 23.40 · Pending" shows the **real photo** as its 46 px thumbnail (not the grey tile), and the pending total rises by exactly 23.40. No duplicate claim.

#### STF-CLM-16 — Submit a claim without a receipt
- [ ] Pass
- Account: Amir
- Pre: on "New claim" (any environment), receipt field untouched
- Steps: 1. Fill category, title, amount, date. 2. Tap **Submit claim**. 3. Tap the left tile of the new row.
- Expect: the claim is accepted exactly as before (a receipt is optional — no warning, no block). Its row shows a neutral grey receipt-icon tile; tapping it does nothing — no viewer, no broken image, no spinner.

#### STF-CLM-17 — Full-screen receipt viewer from the list
- [ ] Pass
- Account: Amir
- Pre: a claim with a receipt exists (STF-CLM-15, or one created through Swagger when on the simulator)
- Steps: 1. On Claims, tap the photo thumbnail. 2. Close it. 3. Force-quit, relaunch and open it again. 4. Sign in as Amir on a second device and open it there.
- Expect: the viewer title is the claim title and the caption is "RM <amount> · <date>" (e.g. "RM 23.40 · 2 Oct"); the photo is the one that was taken, complete and upright; tap anywhere or X closes it and the list is unchanged. It still loads after a relaunch and on the second device (it is stored on the server, not on the phone). If the photo cannot be fetched the row falls back to the grey tile, and the viewer shows "Couldn't load this photo. Close and try again." — never a broken image.

#### STF-CLM-18 — Camera permission denied → claim still goes in
- [ ] Pass
- Account: Amir — **Physical device only** (the iOS Simulator has no camera)
- Pre: camera permission **denied** for Teamora (deny the prompt, or switch Camera off in system Settings)
- Steps: 1. On "New claim", tap **Add receipt photo**. 2. Tap it a second time. 3. Fill the form and tap **Submit claim**.
- Expect: no camera opens and nothing crashes; a grey note appears under the field: "Camera access is off, so this claim will go in without a photo. To attach one, allow Camera for Teamora in your phone's Settings." A second tap shows the same note (iOS does not prompt again; Android may ask once more). The rest of the form works and the claim is submitted **without** a receipt (grey tile in the list; approver sees "No receipt attached", MGR-17).

#### STF-CLM-19 — Size cap: a photo over 2 MB is refused and no claim is created
- [ ] Pass
- Account: Amir (Swagger with his token), then Amir on a phone — step 4 is **Physical device only** (the iOS Simulator has no camera)
- Pre: note the number of claims and the pending total
- Steps: 1. `POST /api/claims` with a valid body plus a `receiptBase64` that decodes to more than 2 MB. 2. The same with `"receiptBase64": "not-base64!!"`. 3. `GET /api/claims`. 4. On the phone, photograph a large, detailed receipt close up and submit.
- Expect: 1 → **400** "Photo too large (max 2 MB). Please retake."; 2 → **400** "Invalid photo encoding"; 3 → neither call created a claim and the pending total is unchanged. 4 → the app never shows the 400: it compresses (and re-shoots harder if needed) so the claim goes in; at worst the camera closes with the note "That photo came out too large to upload. Try again a little further from the receipt, or submit without one." and the claim can still be submitted.

#### STF-CLM-20 — Only the claimant and their approver can fetch a receipt (API)
- [ ] Pass
- Account: Swagger, with tokens for Amir, Nadia, Sarah, owner, Arjun, a second manager, `admin@nusantara.com`
- Pre: Amir has a claim with a receipt and one without; take both ids from `GET /api/claims`; promote the QA employee to Manager for the "second manager" (ADM-EMP-14)
- Steps: 1. `GET /api/claims/{id}/receipt` as Amir, Nadia (his manager), Sarah and the owner. 2. As Arjun (another employee in the same company). 3. As the second manager (not Amir's manager). 4. As `admin@nusantara.com` and `budi@nusantara.com`. 5. With no token. 6. As Amir for the claim **without** a receipt.
- Expect: 1 → **200** with an `image/jpeg` body each time; 2 → **403**; 3 → **403**; 4 → **404** (another company cannot even tell the claim exists); 5 → **401**; 6 → **404**. In the app, Arjun's and Nusantara's Claims / Approvals screens never list Amir's claim or show his receipt.

### B.6 Overtime (`app/(staff)/overtime-submit.tsx`)

#### STF-OT-01 — Log overtime (happy path)
- [ ] Pass
- Account: Amir
- Pre: Attendance → **Log overtime**
- Steps: 1. **Date worked**: today. 2. **Hours**: `2.5`. 3. **Reason (optional)**: "Stock take". 4. Tap **Submit overtime**.
- Expect: "Submitting…", the form closes back to Attendance. Re-open **Log overtime**: "Your overtime" lists "<2.5 h label> · <date>" with the reason on one line and a **Pending** chip.

#### STF-OT-02 — Hours validation
- [ ] Pass
- Account: Amir
- Pre: on "Log overtime"
- Steps: 1. Submit blank. 2. `0.25`. 3. `25`. 4. `abc`. 5. `0.5`. 6. `24`. 7. `2,5`.
- Expect: 1–4 → "Enter hours between 0.5 and 24."; 0.5 and 24 are accepted (boundaries); `2,5` is read as 2.5 and accepted.

#### STF-OT-03 — Future dates cannot be picked
- [ ] Pass
- Account: Amir
- Pre: on "Log overtime"
- Steps: 1. Open **Date worked** and try to select tomorrow.
- Expect: tomorrow and later are not selectable; past dates are.

#### STF-OT-04 — Empty and decided states
- [ ] Pass
- Account: a new employee, then Amir after MGR-07
- Pre: none
- Steps: 1. New employee opens Log overtime. 2. Amir opens it after his OT was approved.
- Expect: new employee sees "No overtime logged yet."; Amir's row chip reads Approved (or Rejected).

### B.7 Calendar (`app/(staff)/(tabs)/calendar.tsx`)

#### STF-CAL-01 — Month grid and today marker
- [ ] Pass
- Account: Amir
- Pre: none
- Steps: 1. Open the Calendar tab.
- Expect: title "Calendar"; month label for the current month; weekday header S M T W T F S; day 1 sits under the correct weekday; **today** is a coral tile with white text; legend Leave / Holiday / Town hall / Event.

#### STF-CAL-02 — Month navigation, including year wrap
- [ ] Pass
- Account: Amir
- Pre: on Calendar
- Steps: 1. Tap the right chevron until January of next year, then one more. 2. Tap the left chevron back past the current month into last year.
- Expect: label and grid update each tap; December → January rolls the year forward and January → December rolls it back; today's coral tile only appears in the current month; no blank / "2026-13" style label after loading.

#### STF-CAL-03 — Company events appear with the right colour
- [ ] Pass
- Account: Sarah then Amir
- Pre: Sarah creates one Holiday, one Event and one Townhall this month (ADM-CAL-02..04)
- Steps: 1. Amir opens Calendar (pull to the month).
- Expect: each date shows a dot in the legend colour for its type (at most 3 dots per day); the items appear under **Upcoming** with title and date label if they are in the future.

#### STF-CAL-04 — Approved leave appears on the calendar
- [ ] Pass
- Account: Amir
- Pre: an approved leave request this month
- Steps: 1. Open that month.
- Expect: leave dates carry the Leave-coloured dot.

#### STF-CAL-05 — "Nothing coming up" and error state
- [ ] Pass
- Account: owner of a fresh company (AUTH-17) via a staff login, or any staff with no future events
- Pre: none
- Steps: 1. Open Calendar. 2. Turn on Airplane mode and change month.
- Expect: Upcoming shows "Nothing coming up."; offline shows "Couldn't load this. Check your connection." + **Try again** in the Upcoming area while the grid stays usable.

### B.8 Payslip (`app/(staff)/(tabs)/payroll.tsx`, `src/lib/payslipPdf.ts`)

#### STF-PAY-01 — Payslip layout and arithmetic
- [ ] Pass
- Account: Amir
- Pre: payroll has been run for the current month (ADM-PAY-02)
- Steps: 1. Open the Payroll tab.
- Expect: title "Payslip" with the period chip; espresso hero "Net pay · take-home" + "RM <net>"; **Earnings** card with "Basic salary" and only non-zero Overtime / Claims reimbursed / Performance bonus rows; sunken "Gross pay" row = sum of the earnings rows; **Deductions** card with EPF, SOCSO, EIS, "PCB (est.) · income tax" and "Total deductions" = their sum; **Net = Gross − Total deductions** to the sen. All money is right-aligned with tabular digits.

#### STF-PAY-02 — Hero pill tells the truth about payment status
- [ ] Pass
- Account: Amir
- Pre: run exists but is still **Draft** (not approved / not paid)
- Steps: 1. Read the pill under the net amount. 2. After the admin marks the run paid (ADM-PAY-08), read it again.
- Expect: a draft / approved payslip must **not** say "Paid"; only a paid payslip says "Paid <date>". The bank shown must be the employee's own bank details (or nothing) — never a fixed sample account.

#### STF-PAY-03 — Unpaid-leave note
- [ ] Pass
- Account: Amir
- Pre: approved Unpaid Leave in the period and payroll re-run (PAY-03)
- Steps: 1. Open Payslip.
- Expect: an amber note "Unpaid leave · <n days>" with "− RM <deduction> · RM <daily rate>/day — already reflected in basic salary."; Basic salary is lower by exactly that deduction.

#### STF-PAY-04 — PCB disclaimer
- [ ] Pass
- Account: Amir
- Pre: payslip present
- Steps: 1. Scroll to the bottom.
- Expect: the caption "PCB is an estimate — it doesn't include year-to-date tax already paid, so your final tax may differ." is visible and the row is labelled "PCB (est.)".

#### STF-PAY-05 — Download payslip (PDF) and share
- [ ] Pass
- Account: Amir
- Pre: payslip present
- Steps: 1. Tap **Download payslip (PDF)**. 2. In the share sheet choose Save to Files (iOS) / a files app (Android). 3. Open the saved PDF.
- Expect: button shows "Preparing…" and is disabled; the native share sheet opens titled "Payslip · <period>"; the PDF shows the same employee, period and every amount as the screen, fits one page, with no clipped text. Cancelling the share sheet returns to the screen with the button re-enabled.

#### STF-PAY-06 — No payslip yet
- [ ] Pass
- Account: a newly added employee
- Pre: no payroll run includes them
- Steps: 1. Open Payroll.
- Expect: chip shows "—"; empty state "No payslips yet — Your payslips will appear here once payroll runs."; no download button.

#### STF-PAY-07 — Period chip behaviour
- [ ] Pass
- Account: Amir
- Pre: payslips exist for two periods
- Steps: 1. Tap the period chip (it shows a down-chevron).
- Expect: either a period picker lets you open an older payslip, or the chip has no chevron. The screen currently shows only the latest payslip — a chevron that does nothing is a fail.

### B.9 Notifications (`app/(staff)/notifications.tsx`)

#### STF-NOT-01 — The Notifications screen is reachable in-app
- [ ] Pass
- Account: Amir
- Pre: signed in (no push tap involved)
- Steps: 1. From Home, find the control that opens Notifications.
- Expect: an in-app entry point (e.g. a bell) opens "Notifications". If the only way in is tapping a push banner, log it — users in Expo Go / without push can never see their feed.

#### STF-NOT-02 — Feed content and unread dot
- [ ] Pass
- Account: Amir
- Pre: Nadia has just approved one of Amir's leave requests
- Steps: 1. Open Notifications.
- Expect: under **Today**, an item "Leave approved — Your Annual Leave was approved." with a coral unread dot and a relative time label; older items sit under **Earlier**.

#### STF-NOT-03 — Mark all read
- [ ] Pass
- Account: Amir
- Pre: at least one unread item
- Steps: 1. Tap **Mark all read** in the header.
- Expect: every coral dot disappears and the **Mark all read** action itself disappears (it only shows while unread > 0). Re-opening the screen keeps everything read.

#### STF-NOT-04 — Empty feed
- [ ] Pass
- Account: a newly added employee
- Pre: no notifications
- Steps: 1. Open Notifications.
- Expect: "You're all caught up — No notifications yet — we'll let you know when something needs you."; no header action.

#### STF-NOT-05 — Every request type notifies both sides
- [ ] Pass
- Account: Amir and Nadia
- Pre: none
- Steps: 1. Amir submits one leave, one claim and one overtime. 2. Nadia opens Notifications. 3. Nadia approves the leave, declines the claim, approves the overtime. 4. Amir opens Notifications.
- Expect: Nadia has three approval-request items (leave reads "Amir Hakim requested Annual Leave (<duration>)"). Amir has "Leave approved", a claim-declined item and an overtime-approved item. Nobody receives a notification about their own action.

### B.10 Profile & My details (`app/(staff)/(tabs)/profile.tsx`, `my-details.tsx`)

#### STF-PRO-01 — Identity card and stats are real
- [ ] Pass
- Account: Amir
- Pre: none
- Steps: 1. Open Profile.
- Expect: "Amir Hakim", "Sales Executive", department chip "Retail"; tiles **Tenure** (years since 1 Feb 2024, one decimal + "yrs"), **Leave left** (sum of remaining across types, rendered cleanly — e.g. "22.5 days", never 22.499999), **Staff ID** "EMP-042".

#### STF-PRO-02 — Menu rows either navigate or do not exist
- [ ] Pass
- Account: Amir
- Pre: on Profile
- Steps: 1. Tap every row in the menu card. 2. Tap the edit (pencil) button in the header, if present.
- Expect: **every visible row and button opens a real screen**. First card: **My details** → My details (STF-PRO-05), **Change password** → Change password (AUTH-26). Second card: **Payslips** → the Payslip tab, **Leave**, **Claims**, **Notifications**. Rows with no destination — "Personal information", "Employment details", "Documents & contracts", "App settings", "Privacy & security" — and a pencil that does nothing must **not be shown**. No row displays an invented count.

#### STF-PRO-03 — Missing optional fields
- [ ] Pass
- Account: an employee created via Add employee with no job title / department
- Pre: none
- Steps: 1. Open Profile.
- Expect: job title and department show "—"; Tenure "—" when no join date; Staff ID "—"; nothing shows "null" or "undefined".

#### STF-PRO-04 — Log out
- [ ] Pass
- Account: Amir
- Pre: on Profile
- Steps: 1. Tap **Log out**.
- Expect: Onboarding screen (see AUTH-23).

**My details** (STF-PRO-05 → 12). Profile → **My details**: a read-only view of
the employee's own record plus the one thing they may change themselves — their
phone number (`PATCH /api/employees/me`). Staff app only (employees and managers).

#### STF-PRO-05 — My details shows what HR has on file
- [ ] Pass
- Account: Amir
- Pre: on Profile
- Steps: 1. Tap **My details** (first row of the first card). 2. Read the card top to bottom. 3. Compare with Sarah → Staff → Amir Hakim.
- Expect: title "My details", subtitle "Amir Hakim"; one card with eight rows in this order, label on the left and value bold on the right: **Email** amir@lumi.com · **Staff ID** EMP-042 · **Job title** Sales Executive · **Department** Retail · **Reporting manager** Nadia Rahman · **Work location** (the assigned site name; if no site is assigned, the location on file) · **Joined** 1 Feb 2024 · **Role** Employee. Under the card: "Something wrong here? Only your HR admin can change these — let them know.", then a **Phone** field and a **Save phone number** button. Every value equals what the admin hub shows — nothing invented.

#### STF-PRO-06 — The eight rows are read-only
- [ ] Pass
- Account: Amir
- Pre: on My details
- Steps: 1. Tap and long-press each of the eight rows and their values.
- Expect: nothing opens and no keyboard appears; the rows have no chevron, pencil or input styling. **Phone** is the only editable control on the screen.

#### STF-PRO-07 — Missing values read "Not set"; no manager reads as the owner approving
- [ ] Pass
- Account: an employee created via Add employee with no job title / department / staff ID / join date / manager; then Nadia
- Pre: none
- Steps: 1. Open My details as the new employee. 2. Open My details as Nadia.
- Expect: empty fields show a grey "Not set" (Staff ID, Job title, Department, Joined, and Work location when none) — never "null", "undefined", a dash or an invented value; **Reporting manager** reads "Company owner approves"; Role "Employee". Nadia: Role "Manager" and Reporting manager "Company owner approves" (she has none). Long values wrap to at most two lines without overlapping the label.

#### STF-PRO-08 — Add and change my phone number
- [ ] Pass
- Account: Amir
- Pre: on My details; phone empty (placeholder "Not set — e.g. 012-345 6789")
- Steps: 1. Check the button before typing. 2. Type `0123456789` (a phone keypad appears). 3. Tap **Save phone number**. 4. Go back to Profile and open My details again. 5. Change it to `0198765432` and save. 6. Force-quit and relaunch.
- Expect: **Save phone number** is disabled until the text differs from the saved value; on save it reads "Saving…", then a sage tick with "Phone number saved." appears and the button is disabled again. The number is still there after re-opening and after a relaunch (step 6 shows `0198765432`). Typing again hides the "saved" line. The eight read-only rows are unchanged.

#### STF-PRO-09 — Clear my phone number
- [ ] Pass
- Account: Amir
- Pre: a phone number is saved (STF-PRO-08)
- Steps: 1. Delete the whole number. 2. Tap **Save phone number**. 3. Re-open My details.
- Expect: the button is enabled once the field is empty; after saving, "Phone number removed." is shown; on re-opening the field is empty with the placeholder "Not set — e.g. 012-345 6789" — not "null" and not the old number.

#### STF-PRO-10 — Phone: no-op, too long, and save failure
- [ ] Pass
- Account: Amir
- Pre: on My details with a saved number
- Steps: 1. Type one digit, then delete it. 2. Paste a 33-character value and tap **Save phone number**. 3. Enter a valid number, turn on Airplane mode, tap save. 4. Turn Airplane mode off and tap save.
- Expect: 1 → the button goes back to disabled (nothing to save). 2 → "Keep it to 32 characters or fewer." under the field; nothing is sent. 3 → "Could not save. Check your connection and try again." under the field; the typed value is kept and no "saved" line shows. 4 → saves normally. Exactly 32 characters is accepted.

#### STF-PRO-11 — An employee cannot change anything but their phone (API)
- [ ] Pass
- Account: Amir (Swagger with his token)
- Pre: note Amir's name, job title, role and salary on the admin hub
- Steps: 1. `PATCH /api/employees/me` with `{"phone":"0111111111","fullName":"Hacked","jobTitle":"CEO","email":"x@lumi.com","role":"OWNER","monthlySalary":99999}`. 2. `PATCH /api/employees/{amirId}` with `{"jobTitle":"CEO"}`. 3. `PATCH /api/employees/me` with a 33-character phone. 4. `PATCH /api/employees/me` with `{"phone":"   "}`. 5. Step 1 with no token.
- Expect: 1 → **200** and only `phone` changed — name, job title, email, role and salary in the response, in My details and on the admin hub are exactly as before, and Amir still signs in with `amir@lumi.com`. 2 → **403**. 3 → **400** "Phone number must be at most 32 characters". 4 → 200 with `phone` null (blank clears). 5 → **401**.

#### STF-PRO-12 — HR's changes show up in My details
- [ ] Pass
- Account: Sarah, then Amir
- Pre: Amir signed in on a second device
- Steps: 1. Sarah changes Amir's job title (hub → Profile) and reporting manager to None (hub → Employment). 2. Amir re-opens My details (relaunch if needed). 3. Sarah restores both.
- Expect: Job title shows the new value and Reporting manager reads "Company owner approves"; after step 3 it reads "Nadia Rahman" again. Amir's saved phone number is not affected by Sarah's saves. Note whether a relaunch was needed to see the change.

### B.11 Staff tab bar

#### STF-NAV-01 — Pill tab bar
- [ ] Pass
- Account: Amir
- Pre: none
- Steps: 1. Tap each tab: Home, Attendance, Calendar, Payroll, Profile. 2. On each, scroll to the very bottom.
- Expect: the active tab expands to icon + label with a light haptic; the last content on every tab clears the floating bar (nothing hidden behind it). Pushed screens (Leave, Claims, Clock In, forms, Notifications) show **no** tab bar.

---

## C. Manager (staff app + Approvals inbox)

Account: **`nadia@lumi.com`** (MANAGER). Direct reports: Amir, Arjun, Siti.
Faizal, Wei Jie, Priya and Mei Ling report to Sarah and must **never** appear in
Nadia's inbox. Screen: `app/(staff)/approvals.tsx` + `ApprovalCards`.

#### MGR-01 — Manager uses the staff app, with an Approvals entry
- [ ] Pass
- Account: Nadia
- Pre: signed in
- Steps: 1. Check the tab bar. 2. Tap the **Approvals** card on Home.
- Expect: staff tabs only (no Staff / Approve / admin Payroll tabs). The card opens "Approvals — Requests from your team" with three tabs **Leave · Claims · OT**, each with a count badge. Nadia can also clock in, apply for leave, claim and view her own payslip exactly like an employee (spot-check one of each).

#### MGR-02 — Inbox shows only direct reports
- [ ] Pass
- Account: Amir, Faizal, then Nadia
- Pre: Amir submits a leave request; Faizal submits a leave request
- Steps: 1. Nadia opens Approvals → Leave.
- Expect: Amir's request is listed; **Faizal's is not** (he reports to Sarah). The Leave badge equals the number of cards.

#### MGR-03 — Approve a full-day leave
- [ ] Pass
- Account: Nadia
- Pre: Amir's pending 2-day Annual Leave (STF-LVE-05); note Amir's Annual balance
- Steps: 1. On Amir's card read the name, "<n> days left" line, the type chip, the date range and the duration chip. 2. Tap **Approve**.
- Expect: both buttons dim while the request is in flight; the card disappears and the Leave badge drops by 1. Amir: request chip → Approved, Annual remaining reduced by exactly 2, a "Leave approved" notification.

#### MGR-04 — Partial-day requests are clearly flagged
- [ ] Pass
- Account: Nadia
- Pre: Amir's pending half-day (STF-LVE-07) and 2-hour (STF-LVE-09) requests
- Steps: 1. Read the duration chip on each card. 2. Approve both.
- Expect: the duration chips are **amber** (full-day requests use a neutral chip) and state the half (AM/PM) or the hours; a single date is shown, not a range. After approval Amir's balance falls by 0.5 and 0.25.

#### MGR-05 — Decline a leave request
- [ ] Pass
- Account: Nadia
- Pre: a pending leave from Arjun
- Steps: 1. Tap **Decline**.
- Expect: the card disappears; Arjun sees a Rejected chip, an unchanged balance and a "Leave declined" notification.

#### MGR-06 — Approve and decline claims
- [ ] Pass
- Account: Nadia
- Pre: two pending claims from Amir (STF-CLM-02 + one more)
- Steps: 1. Open the **Claims** tab. 2. Read a card: name, "<title> · <date>", "RM <amount>". 3. **Approve** one, **Decline** the other.
- Expect: both cards leave the list and the badge drops by 2; Amir's Claims screen reflects both decisions (STF-CLM-08).

#### MGR-07 — Approve overtime
- [ ] Pass
- Account: Nadia
- Pre: Amir's pending 2.5 h overtime (STF-OT-01)
- Steps: 1. Open the **OT** tab. 2. Read the card: name, work date, hours chip, reason. 3. Tap **Approve**.
- Expect: the card disappears; Amir's "Your overtime" row reads Approved. This overtime will be paid in the next payroll run for that month (PAY-05).

#### MGR-08 — Empty tabs
- [ ] Pass
- Account: Nadia
- Pre: nothing pending in a tab
- Steps: 1. Open each empty tab.
- Expect: a card with a check icon — "No leave to review" / "No claims to review" / "No overtime to review" — and "You're all caught up."; badge shows 0.

#### MGR-09 — A manager's own requests go to the owner, not to herself
- [ ] Pass
- Account: Nadia, then owner
- Pre: Nadia has no reporting manager
- Steps: 1. Nadia applies for leave. 2. Nadia opens her own Approvals inbox. 3. `owner@lumi.com` opens Approve → Leave.
- Expect: Nadia's request does **not** appear in her own inbox; it appears for the owner (and for Sarah, who as HR Admin sees everything). The owner received the "New leave request" notification.

#### MGR-10 — Null reporting manager routes to the owner
- [ ] Pass
- Account: Sarah, a staff member, owner, Nadia
- Pre: Sarah opens Arjun → Employment → **Reporting manager: None (Owner)** → Save employment
- Steps: 1. Arjun submits a claim. 2. Nadia opens Approvals → Claims. 3. Owner opens Approve → Claims.
- Expect: Nadia no longer sees Arjun's claim; the owner does and can approve it; the owner got the notification. Restore Arjun's manager to Nadia afterwards.

#### MGR-11 — Reassigning a report moves the pending request
- [ ] Pass
- Account: Sarah, Nadia
- Pre: Amir has a pending leave visible to Nadia
- Steps: 1. Sarah changes Amir's reporting manager to Sarah. 2. Nadia refreshes Approvals.
- Expect: Amir's request is gone from Nadia's inbox. Restore the manager afterwards and confirm it returns.

#### MGR-12 — Two approvers, one request (stale card)
- [ ] Pass
- Account: Nadia on device A, Sarah on device B
- Pre: both have Amir's pending leave on screen
- Steps: 1. Sarah approves it. 2. Without refreshing, Nadia taps **Approve** on the same card.
- Expect: the second decision is refused ("Only pending requests can be decided"); Nadia is told something went wrong (not a silent no-op) and the card clears on refresh; Amir's balance is deducted **once**.

#### MGR-13 — Decision failure is visible
- [ ] Pass
- Account: Nadia
- Pre: a pending card on screen; enable Airplane mode
- Steps: 1. Tap **Approve**. 2. Restore the network.
- Expect: the user is told the action failed and the card remains so it can be retried; the buttons re-enable. A card that silently stays with no message is a fail to log.

#### MGR-14 — Accidental decline protection
- [ ] Pass
- Account: Nadia
- Pre: a pending leave card
- Steps: 1. Tap **Decline** once.
- Expect: note the behaviour — the decision is applied immediately with no confirmation and no undo. Confirm this is still the intended product behaviour for this release; a decision cannot be reversed in the app.

#### MGR-15 — Manager cannot reach admin-only features
- [ ] Pass
- Account: Nadia
- Pre: signed in
- Steps: 1. Look through every tab and Profile. 2. (Optional, Swagger with Nadia's token) call `POST /api/admin/payroll/run`, `POST /api/employees`, `PATCH /api/admin/company-settings`.
- Expect: no route to payroll run, staff editing, company settings, work locations or calendar admin in the UI; the API calls return **403**.

#### MGR-16 — Claim card shows the receipt and opens it full size
- [ ] Pass
- Account: Nadia
- Pre: Amir has a pending claim **with** a receipt (STF-CLM-15 — captured on a physical device, the iOS Simulator has no camera; or created through Swagger)
- Steps: 1. Approvals → **Claims**. 2. Read the strip under Amir's name and amount. 3. Tap the photo. 4. Close the viewer. 5. Tap **Approve**.
- Expect: the strip shows a 52 px thumbnail of the real receipt with "Receipt attached" and "Tap the photo to view it full size". The viewer title is the claim title and the caption is "Amir Hakim · RM <amount>"; the whole receipt is visible and readable. Closing it returns to the inbox with the card still pending — looking at a receipt never decides the claim. Approve then behaves as MGR-06.

#### MGR-17 — Claim without a receipt says so
- [ ] Pass
- Account: Nadia
- Pre: Amir has a pending claim **without** a receipt (STF-CLM-16)
- Steps: 1. Approvals → **Claims**. 2. Read the strip on that card and tap it. 3. Tap **Decline** (or Approve).
- Expect: the strip reads "No receipt attached" with a small receipt icon — no thumbnail, no grey photo box, and nothing happens on tap. **Approve** and **Decline** are both still available (a receipt is optional) and work as MGR-06.

---

## D. Admin app (OWNER / HR_ADMIN)

Default account: **`sarah@lumi.com`** (HR Admin). Use **`owner@lumi.com`** where
the case says Owner.

### D.1 Dashboard (`app/admin/(tabs)/dashboard.tsx`)

#### ADM-DASH-01 — Header and role
- [ ] Pass
- Account: Sarah, then Owner
- Pre: signed in
- Steps: 1. Open Home tab. 2. Scroll. 3. Tap the avatar.
- Expect: title "Good <morning/afternoon/evening>, Sarah"; subtitle "<Day>, <D Mon> · HR Admin" (Owner sees "· Owner"); header collapses on scroll; tapping the avatar opens the admin **Profile** tab.

#### ADM-DASH-02 — KPI tiles match the source screens
- [ ] Pass
- Account: Sarah
- Pre: some staff clocked in today; some requests pending
- Steps: 1. Read the four tiles. 2. Compare with Live attendance, the Approve tab and the Payroll tab.
- Expect: "<present> /<headcount> present" = people clocked in today ÷ active staff (Staff tab count); "on leave" = staff on leave today; "approvals" = the Approve tab's "<n> pending" chip; "RM <x> payroll due" = the net of the current period's run (or the latest run; "RM 0.00" when none).

#### ADM-DASH-03 — Attendance this week chart
- [ ] Pass
- Account: Sarah
- Pre: clock-ins exist this week
- Steps: 1. Read the chart and the rate chip.
- Expect: one bar per weekday, today's bar highlighted, heights proportional to people present; the rate chip is a real percentage; an empty week draws empty bars, not sample data.

#### ADM-DASH-04 — Recent activity
- [ ] Pass
- Account: Sarah
- Pre: a staff member submits a leave, a claim and an overtime
- Steps: 1. Wait up to 60 s or re-open the tab.
- Expect: each appears as a row with the matching icon (leave / receipt / clock), a sentence naming the person, and a time label; newest first. A fresh company shows "No activity yet".

#### ADM-DASH-05 — Quick access
- [ ] Pass
- Account: Sarah
- Pre: on Dashboard
- Steps: 1. Tap **Live attendance** → back. 2. Tap **Week schedule** → back.
- Expect: opens "Live Attendance" and "Scheduling" as pushed screens with a back button and no tab bar.

#### ADM-DASH-06 — Dashboard error state
- [ ] Pass
- Account: Sarah
- Pre: Airplane mode
- Steps: 1. Open Dashboard. 2. Restore network, tap **Try again**.
- Expect: "Couldn't load this. Check your connection." with **Try again**; Quick access cards remain usable; retry loads the KPIs.

### D.2 Live attendance (`app/admin/live.tsx`, `SelfieThumb`)

#### ADM-LIVE-01 — Counts and list
- [ ] Pass
- Account: Sarah
- Pre: Amir clocked in on time, Arjun clocked in late, others not in
- Steps: 1. Dashboard → **Live attendance**.
- Expect: title "Live Attendance", subtitle "Updates every 30s", a "LIVE" chip with a blinking dot. Tiles **In / Remote / Late / Out**: a late person counts in both In and Late; everyone not clocked in counts as Out; In + Remote + Out = active headcount. Every active employee is listed A–Z with department, a status chip and a time.

#### ADM-LIVE-02 — Auto-refresh
- [ ] Pass
- Account: Sarah on device A, a staff member on device B
- Pre: Live board open on A
- Steps: 1. Staff clocks in on B. 2. Wait up to 30 s on A without touching it.
- Expect: the row flips to the clocked-in status and the counts update by themselves.

#### ADM-LIVE-03 — Selfie thumbnail and enlarged viewer
- [ ] Pass
- Account: Sarah
- Pre: a staff member clocked in **with** a selfie on a physical device (DEV-01)
- Steps: 1. Find their row. 2. Tap the thumbnail. 3. Tap anywhere to close.
- Expect: the row shows a small rounded photo instead of the initial; tapping opens a dark overlay with the square photo, the person's name and "Clock-in photo · tap to close"; tapping closes it. Android back also closes it.

#### ADM-LIVE-04 — No photo → initial avatar, not tappable
- [ ] Pass
- Account: Sarah
- Pre: a staff member clocked in without a photo (STF-CLK-04); another not clocked in
- Steps: 1. Tap both avatars.
- Expect: tinted initial avatars; tapping does nothing; no broken-image icon.

#### ADM-LIVE-05 — Filter chips work or are absent
- [ ] Pass
- Account: Sarah
- Pre: mixed statuses on the board
- Steps: 1. Tap **In office**, **Remote**, **Late**, **All**.
- Expect: the list filters to the chosen status and the chosen chip becomes the dark active one. Chips that do nothing ("All" permanently selected) are a fail.

#### ADM-LIVE-06 — No fake map or fixed office name
- [ ] Pass
- Account: owner of a fresh company (AUTH-17)
- Pre: nobody clocked in
- Steps: 1. Open Live attendance.
- Expect: no map with invented pins and no office name that is not the company's own (e.g. a fresh company must not see "Bangsar South HQ"). List shows "Nobody clocked in yet — Live attendance appears here as your team checks in for the day." (or everyone as Out).

### D.3 Staff list (`app/admin/(tabs)/staff.tsx`)

#### ADM-STAFF-01 — List and count
- [ ] Pass
- Account: Sarah
- Pre: fresh reset
- Steps: 1. Open the Staff tab.
- Expect: title "Staff", subtitle "10 employees"; each row shows avatar initial, full name, "<job title> · <department>" and a department chip. Rows with a missing title / department show no "null" text.

#### ADM-STAFF-02 — Search
- [ ] Pass
- Account: Sarah
- Pre: on Staff
- Steps: 1. Type `amir`. 2. Clear with the ✕. 3. Type `EMP-04`. 4. Type `Sales`. 5. Type `zzz`.
- Expect: 1 → Amir only, subtitle "1 employee"; ✕ restores all; `EMP-04` → Amir (EMP-042) and Arjun (EMP-040); `Sales` → the Sales Executives; `zzz` → empty state "No staff match "zzz" — Try a different name, ID or role." Search is case-insensitive.

#### ADM-STAFF-03 — Open an employee
- [ ] Pass
- Account: Sarah
- Pre: on Staff
- Steps: 1. Tap Amir's row.
- Expect: the **Employee** hub opens showing the name, email and role chip immediately (before the tiles finish loading).

#### ADM-STAFF-04 — Add button
- [ ] Pass
- Account: Sarah
- Pre: on Staff
- Steps: 1. Tap the coral **+** in the header.
- Expect: "Add employee" slides up.

#### ADM-STAFF-05 — Fresh company empty state
- [ ] Pass
- Account: —
- Pre: only reachable if a company has zero listed staff
- Steps: 1. Review the empty state copy in code review if it cannot be reproduced.
- Expect: "No team members yet — Add your first employee to start managing your team." with an **Add employee** action. (A new company always lists its owner, so this is normally unreachable.)

### D.4 Add employee (`app/admin/employee-new.tsx`)

#### ADM-EMP-01 — Add an employee (short form) → lands on the hub
- [ ] Pass
- Account: Sarah
- Pre: Staff → **+**
- Steps: 1. **Full name**: "Qa Tester". 2. **Work email**: `qa.tester+<n>@lumi.com`. 3. **Temporary password**: `password`. 4. **Role**: Employee. 5. **Job title (optional)**: "QA Analyst". 6. Tap **Add employee**.
- Expect: intro text "Just the essentials for now…"; button shows "Adding…"; the form is **replaced** by the new person's Employee hub. Tapping back returns to the Staff list (not to the form), where the subtitle count rose by 1.

#### ADM-EMP-02 — Required fields
- [ ] Pass
- Account: Sarah
- Pre: on Add employee
- Steps: 1. Submit with name blank. 2. With email blank. 3. With password blank.
- Expect: "Name, email and password are required." each time; nothing created.

#### ADM-EMP-03 — Duplicate and invalid email
- [ ] Pass
- Account: Sarah
- Pre: on Add employee
- Steps: 1. Use `amir@lumi.com`. 2. Use `budi@nusantara.com` (another company's user). 3. Use `not-an-email`.
- Expect: 1 and 2 → "An account with this email already exists" (emails are unique across all companies); 3 → a validation error. Form values are kept.

#### ADM-EMP-04 — Role choices exclude Owner
- [ ] Pass
- Account: Sarah
- Pre: on Add employee
- Steps: 1. Read the **Role** chips. 2. Create one **Manager** and one **HR Admin**.
- Expect: chips are Employee / Manager / HR Admin only. Signing in as the new Manager lands in the staff app with an Approvals card; the new HR Admin lands in the admin app.

#### ADM-EMP-05 — New employee can sign in and starts clean
- [ ] Pass
- Account: the employee from ADM-EMP-01
- Pre: created
- Steps: 1. Log out, sign in as the new employee. 2. Visit Home, Attendance, Leave, Claims, Payroll, Profile.
- Expect: sign-in works with the temporary password; every screen shows honest empty states; leave tiles show the company defaults (16 / 14 / 5, prorated if a join date is set later); no data from other employees.

### D.5 Employee hub (`app/admin/employee-edit.tsx`)

#### ADM-EMP-06 — Hub layout and tile summaries
- [ ] Pass
- Account: Sarah
- Pre: open Amir from the Staff list
- Steps: 1. Read the identity card and the five tiles.
- Expect: title "Employee"; card with name, email (read-only) and role chip "Employee"; hint "Pick a section to edit. Each one saves on its own — the rest stays as it is."; tiles **Profile** ("Sales Executive · Retail"), **Employment** ("Employee · Nadia Rahman"), **Compensation** ("RM 4,000 · Mon–Fri · 8h"), **Statutory & bank** ("Not set" until filled), **Leave entitlement** ("Annual Leave 16 · Medical Leave 14").

#### ADM-EMP-07 — Tiles for a brand-new employee
- [ ] Pass
- Account: Sarah
- Pre: open the employee from ADM-EMP-01
- Steps: 1. Read each tile.
- Expect: Employment "Employee · Owner approves"; Compensation "No salary · Mon–Fri · 8h"; Statutory & bank "Not set" in muted grey; no tile shows "null", "undefined" or "NaN".

#### ADM-EMP-08 — Each tile opens its own screen and returns with a fresh summary
- [ ] Pass
- Account: Sarah
- Pre: on a hub
- Steps: 1. Tap each tile in turn, change one value, save, and return.
- Expect: titles "Profile", "Employment", "Compensation", "Statutory & bank", "Leave entitlement", each with the employee's name as subtitle; after saving, the hub tile summary shows the new value without a manual refresh.

### D.6 Hub → Profile (`employee-profile.tsx`)

#### ADM-EMP-09 — Edit profile
- [ ] Pass
- Account: Sarah
- Pre: hub of the QA employee → **Profile**
- Steps: 1. Change **Full name**, **Job title**, **Department**. 2. Tap **Save profile**.
- Expect: "Saving…", returns to the hub; the identity card and Profile tile show the new values; the Staff list row and (after the employee re-opens the app) their own Profile screen match. The note "Work email (<email>) can't be changed here — it's the employee's sign-in." is shown.

#### ADM-EMP-10 — Name is required; title / department can be cleared
- [ ] Pass
- Account: Sarah
- Pre: on Profile section
- Steps: 1. Clear **Full name**, save. 2. Restore the name, clear **Job title** and **Department**, save.
- Expect: 1 → "Name is required."; 2 → saved, hub tile shows "Not set", Staff list shows no stray " · ".

#### ADM-EMP-11 — REGRESSION: saving Profile must not wipe manager or work location
- [ ] Pass
- Account: Sarah
- Pre: an employee with **both** a reporting manager (e.g. Nadia) and a work location assigned (ADM-EMP-12). Note both on the hub Employment tile / screen.
- Steps: 1. Hub → **Profile** → change only the **Job title** → **Save profile**. 2. Hub → **Employment**. 3. Repeat by saving **Compensation**, then **Statutory & bank**, then one **Leave entitlement** row, re-opening Employment after each.
- Expect: after **every** save the reporting manager chip is still Nadia and the work location chip is still the assigned site. The employee's next request still routes to Nadia (not the owner) and their clock-in is still geofenced. *(This was a real data-loss bug — any single-section save used to clear both.)*

### D.7 Hub → Employment (`employee-employment.tsx`, `employee-reset-password.tsx`)

#### ADM-EMP-12 — Assign reporting manager, work location and join date
- [ ] Pass
- Account: Sarah
- Pre: at least one active work location exists (ADM-LOC-01); hub of the QA employee → **Employment**
- Steps: 1. **Role**: Employee. 2. **Reporting manager**: "Nadia Rahman · Mgr". 3. **Work location**: the site. 4. **Joined**: pick a date. 5. Tap **Save employment**.
- Expect: manager options list only Owner / HR / Mgr people plus "None (Owner)" — no plain employees; helper text "The manager who approves this employee's leave, claims and overtime. With none, the company owner approves."; after saving the hub tile reads "Employee · Nadia Rahman"; re-opening shows all four values kept.

#### ADM-EMP-13 — Clear manager and work location explicitly
- [ ] Pass
- Account: Sarah
- Pre: employee from ADM-EMP-12
- Steps: 1. **Reporting manager**: None (Owner). 2. **Work location**: None. 3. **Save employment**. 4. Re-open Employment.
- Expect: both show None; hub tile "Employee · Owner approves"; the employee can now clock in anywhere and their requests go to the owner.

#### ADM-EMP-14 — Change role
- [ ] Pass
- Account: Sarah
- Pre: QA employee
- Steps: 1. Set **Role** to Manager → save. 2. The employee signs in again.
- Expect: hub chip "Manager"; the person now appears in other employees' Reporting manager options and sees the Approvals card on Home.

#### ADM-EMP-15 — Cannot change your own role
- [ ] Pass
- Account: Sarah editing **Sarah Lim**
- Pre: Staff → Sarah Lim → Employment
- Steps: 1. Change **Role** to Employee. 2. Save.
- Expect: red message "You cannot change your own role"; role unchanged. Saving with the role left as HR Admin works.

#### ADM-EMP-16 — Owner's role is locked
- [ ] Pass
- Account: Sarah
- Pre: Staff → Imran Yusof → Employment
- Steps: 1. Read the Role area. 2. Save without changes.
- Expect: no role chips; a violet "Owner" chip with "A company has exactly one owner. Change it by transferring ownership from the owner's own account."; saving other fields works and the role stays Owner. No **Transfer ownership** button for Sarah.

#### ADM-EMP-17 — An employee cannot report to themselves
- [ ] Pass
- Account: Sarah
- Pre: Staff → Nadia Rahman → Employment
- Steps: 1. Choose "Nadia Rahman · Mgr" as her own reporting manager. 2. Save.
- Expect: "An employee cannot report to themselves"; nothing saved.

#### ADM-EMP-18 — Inactive site stays visible when already assigned
- [ ] Pass
- Account: Sarah
- Pre: employee assigned to a site that is then set Inactive (ADM-LOC-07)
- Steps: 1. Open the employee's Employment section.
- Expect: the inactive site still appears as the selected chip (by its name) so it is not silently cleared; saving without touching it keeps the assignment.

#### ADM-EMP-19 — Join date: unset state is honest
- [ ] Pass
- Account: Sarah
- Pre: an employee with no join date (created via Add employee)
- Steps: 1. Open Employment and read the **Joined** field and its helper. 2. Save without touching it. 3. Re-open. 4. Try to pick a future date.
- Expect: helper says "Not recorded yet — pick a date to prorate their first-year leave entitlement."; the field must not look like today's date has already been saved; saving untouched leaves it unrecorded; future dates are not selectable. After picking a date the helper reads "Prorates their first-year leave entitlement."

**Reset password** (ADM-EMP-35 → 40). There is no email reset: an OWNER / HR_ADMIN
sets a temporary password from the Employment screen and passes it on. Use
**Arjun** as the target, and restore his password afterwards (reset it to
`password`) or reset the data (0.5).

#### ADM-EMP-35 — Reset password button: who sees it, and for whom
- [ ] Pass
- Account: Sarah, then `owner@lumi.com`
- Pre: Staff tab
- Steps: 1. Sarah: open Employment for Arjun Nair, for Sarah Lim (herself) and for Imran Yusof (the owner). 2. Owner: open Employment for Sarah Lim and for Imran Yusof (himself).
- Expect: a light **Reset password** button sits under **Save employment** for Arjun (Sarah) and for Sarah (owner — together with **Transfer ownership**). It is **not** shown on your own record (your own password goes through Profile → Change password) and **not** shown to Sarah on the owner's record. The button appears only once the employee's details have loaded.

#### ADM-EMP-36 — Reset an employee's password; the new one works at next login
- [ ] Pass
- Account: Sarah, then `arjun@lumi.com`
- Pre: Staff → Arjun Nair → Employment; Arjun signed out
- Steps: 1. Tap **Reset password**. 2. Read the screen. 3. **Temporary password**: `Temp-pass-01`. 4. Tap **Reset password**. 5. Dismiss the alert. 6. Arjun signs in with the old password, then with `Temp-pass-01`. 7. Arjun: Profile → **Change password**, using `Temp-pass-01` as the current password.
- Expect: the screen slides up titled "Reset password" with the subtitle "Arjun Nair"; the field shows what is typed in **plain text** (the admin has to read it out) with the placeholder "At least 8 characters"; an amber note reads "Share this password with Arjun Nair privately — it replaces their current one straight away. Once they're signed in, they can set their own under Profile → Change password." The button reads "Resetting…", then an alert "Password reset" — "Share the temporary password with Arjun Nair so they can sign in." — and the screen closes back to Employment with role, manager, work location and join date unchanged. Step 6: the old password is rejected (AUTH-10 treatment), the temporary one signs in to staff Home. Step 7 succeeds. Sarah herself stays signed in throughout.

#### ADM-EMP-37 — Temporary password validation; leaving does not reset
- [ ] Pass
- Account: Sarah
- Pre: on Reset password for Arjun
- Steps: 1. Tap **Reset password** with the field blank. 2. Enter 7 characters and tap it. 3. Enter 73 characters and tap it. 4. Type a valid password, then tap the back chevron. 5. Arjun signs in with his current password.
- Expect: 1 and 2 → "Use at least 8 characters."; 3 → "Keep it to 72 characters or fewer."; the button never shows "Resetting…" and no alert appears. 4 → returns to Employment with nothing changed. 5 → still works. With the API stopped, a valid submit shows "Could not reset the password. Check your connection and try again." and keeps the typed value.

#### ADM-EMP-38 — A reset signs the employee out on every device
- [ ] Pass
- Account: Sarah; `arjun@lumi.com` signed in on device B **and** device C
- Pre: both of Arjun's devices showing data
- Steps: 1. Sarah resets Arjun's password (ADM-EMP-36). 2. On B and C keep using the app for up to 30 minutes (or shorten the access-token lifetime as in X-SES-01). 3. On B sign in with the old password, then the temporary one.
- Expect: **both** B and C are returned to the signed-out flow (Onboarding / Login) once their access token expires (≤ 30 min) — unlike a self-service change (AUTH-32), no device is spared. Neither can get back in with the old password; the temporary one works. No crash loop and no screen stuck on error boxes. Log how long the devices stayed usable after the reset, and whether a push still arrives on a device that has been signed out this way (compare DEV-09).

#### ADM-EMP-39 — An HR admin cannot reset the owner; the owner can reset an HR admin
- [ ] Pass
- Account: Sarah (app + Swagger with her token), then `owner@lumi.com`
- Pre: the owner's employee id (from `GET /api/employees`)
- Steps: 1. Sarah: Staff → Imran Yusof → Employment — look for **Reset password**. 2. Swagger as Sarah: `POST /api/employees/{ownerId}/reset-password` with `{"newPassword":"Temp-pass-02"}`. 3. Sign in as `owner@lumi.com` with `password`. 4. Owner: Staff → Sarah Lim → Employment → **Reset password** → `Temp-pass-03`. 5. Sarah signs in with `Temp-pass-03`.
- Expect: 1 → no button. 2 → **403** ("Only the owner can reset the owner's password"). 3 → the owner's password is unchanged and his sessions were not ended. 4 → succeeds with the "Password reset" alert. 5 → Sarah signs in with the temporary password (her old one fails). An owner who forgets their own password has **no** in-app recovery — see Known limitations.

#### ADM-EMP-40 — Managers, employees and other companies cannot reset a password (API)
- [ ] Pass
- Account: Swagger with tokens for Nadia, Amir, `admin@nusantara.com`, Sarah
- Pre: Amir's employee id
- Steps: 1. `POST /api/employees/{amirId}/reset-password` with `{"newPassword":"Temp-pass-04"}` as Nadia. 2. As Amir (his own id). 3. As `admin@nusantara.com`. 4. With no token. 5. As Sarah with `{"newPassword":"short"}`. 6. Amir signs in with `password`.
- Expect: 1 → **403**; 2 → **403**; 3 → **404** (another company); 4 → **401**; 5 → **400** ("Password must be 8 to 72 characters"); 6 → still works — none of the refused calls changed his password or signed him out. In the app, Nadia has no route to the Employment screen at all (MGR-15).

### D.8 Transfer ownership

#### ADM-OWN-01 — Only the owner sees Transfer ownership, and not on self
- [ ] Pass
- Account: Owner, then Sarah
- Pre: none
- Steps: 1. Owner opens Sarah → Employment. 2. Owner opens Imran (self) → Employment. 3. Sarah opens any employee → Employment.
- Expect: 1 → **Transfer ownership** button visible under Save employment; 2 → not shown; 3 → not shown.

#### ADM-OWN-02 — Cancel the transfer
- [ ] Pass
- Account: Owner
- Pre: Sarah → Employment
- Steps: 1. Tap **Transfer ownership**. 2. Read the dialog. 3. Tap **Cancel**.
- Expect: dialog "Transfer ownership?" — "Sarah Lim will become the company owner, and you'll become an HR Admin. This can't be undone by you afterwards."; Cancel changes nothing.

#### ADM-OWN-03 — Complete the transfer (run last, or reset afterwards)
- [ ] Pass
- Account: Owner (`owner@lumi.com`)
- Pre: ADM-OWN-02
- Steps: 1. Tap **Transfer ownership** → **Transfer**. 2. Dismiss "Ownership transferred — Sarah Lim is now the owner." 3. Open the Staff list and both hubs. 4. Log out; sign in as Sarah; open Imran → Employment.
- Expect: Sarah's chip is Owner, Imran's is HR Admin; exactly **one** Owner in the company; Sarah (new owner) now sees **Transfer ownership** on Imran; Imran no longer sees it anywhere. Requests from staff with no manager now route to Sarah. Transfer back (or reset data) before continuing.

### D.9 Hub → Compensation (`employee-compensation.tsx`, `CompensationFields`)

#### ADM-EMP-20 — Set salary and see derived rates
- [ ] Pass
- Account: Sarah
- Pre: QA employee → **Compensation**
- Steps: 1. **Monthly salary (RM)**: `4000`. 2. **Pay basis**: Monthly. 3. **Working days**: Mon–Fri on. 4. **Hours per day**: `8`. 5. Read "Derived rates · this month". 6. Tap **Save compensation**.
- Expect: preview shows **RM (4000 ÷ working days this month) per day** and **(that ÷ 8) per hour** with the caption "Mon–Fri · <n> working days this month" — e.g. October 2026: 22 days → RM 181.82 / day, RM 22.73 / hour (see table F.0). Hub tile becomes "RM 4,000 · Mon–Fri · 8h".

#### ADM-EMP-21 — Preview reacts live to the schedule
- [ ] Pass
- Account: Sarah
- Pre: on Compensation with salary 4000
- Steps: 1. Toggle **Sat** on. 2. Toggle every day on. 3. Change hours to `7.5`. 4. Clear the salary.
- Expect: the working-day count and both rates recalculate on every change (every day on → "Every day", days = days in the month); clearing the salary shows "Enter a monthly salary to preview the daily and hourly rates."

#### ADM-EMP-22 — Validation
- [ ] Pass
- Account: Sarah
- Pre: on Compensation
- Steps: 1. Salary `abc` or `-100` → save. 2. Hours `0` → save. 3. Hours `-2` → save.
- Expect: "Enter a valid monthly salary, or leave it blank." / "Enter a valid number of hours per day."; nothing saved.

#### ADM-EMP-23 — Schedule drives leave rules
- [ ] Pass
- Account: Sarah, then the employee
- Pre: set the QA employee's working days to **Tue–Sat** and hours to **6**, save
- Steps: 1. Employee applies Half day on a Monday. 2. Applies Half day on a Saturday. 3. Applies Hours = 7. 4. Reads the Hours hint.
- Expect: Monday → "… isn't one of your working days"; Saturday → accepted; 7 hours → "That's longer than a work day — enter 6 hours or less."; hint says a full work day is 6 hours. Restore Mon–Fri / 8 afterwards.

#### ADM-EMP-24 — Clearing a salary
- [ ] Pass
- Account: Sarah
- Pre: QA employee has salary 4000
- Steps: 1. Clear **Monthly salary (RM)**. 2. Save. 3. Re-open Compensation and read the hub tile.
- Expect: decide the intended behaviour and log the actual one — a blank save currently leaves the old salary in place (the field cannot be cleared from the app), so the tile still says "RM 4,000". If "leave it blank" is meant to remove the salary, this is a bug.

#### ADM-EMP-25 — No working days selected
- [ ] Pass
- Account: Sarah
- Pre: on Compensation
- Steps: 1. Toggle all seven days off. 2. Save.
- Expect: the preview shows "No working days · 0 working days this month" with no rate; the app should refuse to save a schedule with no working days (or log that it allows it — such an employee can never apply for leave and has a zero daily rate).

### D.10 Hub → Statutory & bank (`employee-statutory.tsx`)

#### ADM-EMP-26 — Fill statutory and bank details
- [ ] Pass
- Account: Sarah
- Pre: Amir → **Statutory & bank**
- Steps: 1. Fill **NRIC** `900101-14-5678`, **EPF no.**, **SOCSO no.**, **Tax no. (LHDN)**, **Bank name** `Maybank`, **Bank account no.** `512345678901`. 2. Tap **Save statutory & bank**.
- Expect: saved; hub tile "NRIC set · Maybank · EPF no."; values persist on re-open; they appear in the exports (ADM-EXP-03..05).

#### ADM-EMP-27 — Clear a field
- [ ] Pass
- Account: Sarah
- Pre: fields filled
- Steps: 1. Empty **Bank name** and **Bank account no.**. 2. Save. 3. Re-open.
- Expect: both are blank after re-open; hub tile drops the bank part; the bank export shows blank cells for this person (never an invented account).

#### ADM-EMP-28 — Tax profile drives PCB
- [ ] Pass
- Account: Sarah
- Pre: QA employee with salary 4000, tax profile Single / 0 children
- Steps: 1. **Marital status**: Married → confirm a **Spouse** chooser appears → **Not working**. 2. **Number of children**: `2`. 3. Save. 4. Re-run payroll (draft) and read this employee's PCB.
- Expect: Spouse chooser only shows when Married; PCB falls from **RM 50.00** to **RM 30.00** for a full RM 4,000 month (see PAY-02). Switching Spouse to **Working** with 0 children gives the same PCB as Single (RM 50.00).

#### ADM-EMP-29 — Children validation
- [ ] Pass
- Account: Sarah
- Pre: on Statutory & bank
- Steps: 1. Children `1.5` → save. 2. `-1` → save. 3. Blank → save.
- Expect: 1 and 2 → "Enter a valid number of children, or leave it blank."; blank saves without error and keeps the previous value.

### D.11 Hub → Leave entitlement (`employee-leave.tsx`, `LeaveEntitlementFields`)

#### ADM-EMP-30 — Override an entitlement
- [ ] Pass
- Account: Sarah
- Pre: Amir → **Leave entitlement**
- Steps: 1. Read the caption and rows. 2. Change **Annual Leave** from 16 to `20`. 3. Tap **Save entitlement**. 4. Tap **Done**.
- Expect: caption "Full-year entitlement for the <year> leave year. Accrual and carried-over days are applied on top of this."; each row shows "<used> used" (+ "· <n> carried over" when any); **Save entitlement** is disabled until a value actually changes; after saving "Entitlement saved." appears and the button disables again. Amir's Leave screen now shows remaining = 20 − used, "/20".

#### ADM-EMP-31 — Fractional and invalid values
- [ ] Pass
- Account: Sarah
- Pre: on Leave entitlement
- Steps: 1. Enter `12.5` for Medical → save. 2. Enter `-3` → save. 3. Enter `abc` → save. 4. Clear a field → save.
- Expect: 12.5 is saved and shown as "12.5"; negative, text and blank → "Enter a valid number of days for <type name>." and nothing is saved for any row.

#### ADM-EMP-32 — Lowering below what was taken shows a negative balance
- [ ] Pass
- Account: Sarah, then Amir
- Pre: Amir has used 4 Annual days
- Steps: 1. Set Annual entitlement to `2`. 2. Amir opens Leave.
- Expect: Amir's Annual tile shows **-2** remaining "/2" — the shortfall is visible, not clamped to 0. Restore to 16 afterwards.

#### ADM-EMP-33 — Override is per employee
- [ ] Pass
- Account: Sarah
- Pre: ADM-EMP-30 done for Amir
- Steps: 1. Open Arjun → Leave entitlement.
- Expect: Arjun's Annual entitlement is still the company default (16).

#### ADM-EMP-34 — No leave types
- [ ] Pass
- Account: Sarah
- Pre: every leave type set to Hidden (restore afterwards)
- Steps: 1. Open an employee's Leave entitlement. 2. Tap **Leave types**.
- Expect: "No leave types yet — Set up your company's leave types first, then give this employee their entitlement."; the action opens Company settings.

### D.12 Approvals (`app/admin/(tabs)/approvals.tsx`)

#### ADM-APR-01 — Admin sees every pending request in the company
- [ ] Pass
- Account: Sarah
- Pre: pending requests exist from a Nadia report (Amir) and a Sarah report (Faizal)
- Steps: 1. Open the **Approve** tab.
- Expect: title "Approvals", subtitle "Leave, claims & overtime", amber chip "<total> pending" = Leave + Claims + OT badge counts; **both** Amir's and Faizal's requests are listed (HR Admin / Owner override the manager routing). The tab row stays pinned while the list scrolls.

#### ADM-APR-02 — Approve / decline leave, claims and overtime
- [ ] Pass
- Account: Sarah
- Pre: one pending item of each type
- Steps: 1. Approve a leave. 2. Decline a claim. 3. Approve an overtime.
- Expect: each card disappears, the tab badge and the "<n> pending" chip decrease; the Dashboard "approvals" KPI matches within a minute; requesters see the outcome and a notification.

#### ADM-APR-03 — Balance shown on the card is the requester's
- [ ] Pass
- Account: Sarah
- Pre: Amir has a pending Annual request
- Steps: 1. Read the "<n> days left" line on the card. 2. Compare with Amir's Leave tile.
- Expect: the same figure (for the leave year the request starts in).

#### ADM-APR-04 — Empty state and partial-day flag
- [ ] Pass
- Account: Sarah
- Pre: clear a tab; submit one half-day request from a staff account
- Steps: 1. Open the empty tab. 2. Open the half-day card.
- Expect: "No <leave/claims/overtime> to review — You're all caught up."; the half-day duration chip is amber.

#### ADM-APR-05 — Admin sees claim receipts (or "No receipt attached")
- [ ] Pass
- Account: Sarah
- Pre: two pending claims: one **with** a receipt from an employee who does not report to Nadia (e.g. Faizal — captured on a physical device, the iOS Simulator has no camera; or created through Swagger) and one **without**
- Steps: 1. Approve tab → **Claims**. 2. On the first card tap the receipt photo, then close it. 3. Read the strip on the second card. 4. Approve the first claim. 5. The claimant opens Claims and taps the thumbnail of the now-approved claim.
- Expect: first card: thumbnail + "Receipt attached" / "Tap the photo to view it full size"; the viewer shows the full receipt with the caption "<employee> · RM <amount>" — HR Admin / Owner can open any receipt in their company. Second card: "No receipt attached". After approval the card leaves the queue, and the claimant can still open the receipt from their own list (it is kept after the decision).

### D.13 Payroll run (`app/admin/(tabs)/payroll.tsx`)

#### ADM-PAY-01 — No run yet
- [ ] Pass
- Account: Sarah
- Pre: fresh reset; current month has no run
- Steps: 1. Open the **Payroll** tab.
- Expect: title "Payroll", subtitle = current month; no status chip; card "No payroll run yet — Generate this month's draft payslips from staff salaries, approved overtime and claims." with **Run payroll**.

#### ADM-PAY-02 — Run payroll (draft)
- [ ] Pass
- Account: Sarah
- Pre: ADM-PAY-01
- Steps: 1. Tap **Run payroll**.
- Expect: "Running…" then: status chip **Draft** (amber); hero "Net disbursement RM <net>" with "<n> employees" and "Pay <last day of month>"; totals card Gross salary / Statutory (EPF + SOCSO + EIS) / PCB (est.) / Net disbursement where **Net = Gross − Statutory − PCB**; a **Breakdown** list "<n> payslips" A–Z with Basic / OT / Claims / Deductions and net per person; buttons **Approve payroll**, **Re-run**, **Reports & export**. Sum of the per-person nets = the hero figure.

#### ADM-PAY-03 — Skipped employees are reported
- [ ] Pass
- Account: Sarah
- Pre: at least one active employee with no salary (e.g. the one from ADM-EMP-01 before ADM-EMP-20)
- Steps: 1. Run payroll.
- Expect: amber note "1 employee skipped — no salary set. Add a monthly salary to include them."; that person has no line. After setting a salary and tapping **Re-run** the note disappears and their line appears.

#### ADM-PAY-04 — Re-run refreshes a draft
- [ ] Pass
- Account: Sarah
- Pre: draft exists; then approve a new claim for Amir dated this month
- Steps: 1. Tap **Re-run**.
- Expect: "Re-running…"; Amir's Claims figure and net rise by the claim amount; other lines unchanged; still **Draft**; no duplicate lines.

#### ADM-PAY-05 — Approve payroll
- [ ] Pass
- Account: Sarah
- Pre: draft
- Steps: 1. Tap **Approve payroll**. 2. In "Approve payroll?" tap **Cancel**. 3. Tap **Approve payroll** → **Approve**.
- Expect: dialog text "This locks <Month Year> for payment. You can still mark it as paid afterwards."; Cancel changes nothing; after Approve the chip turns **Approved** (violet) and the only lifecycle button is **Mark as paid** (no Re-run).

#### ADM-PAY-06 — Approved payslips are never overwritten
- [ ] Pass
- Account: Sarah
- Pre: run is Approved; note Amir's net
- Steps: 1. Approve a new overtime or claim for Amir dated this month. 2. Re-open Payroll. 3. (Swagger, Sarah's token) `POST /api/admin/payroll/run` with `{"period":"<YYYY-MM>"}`. 4. Re-open Payroll and Amir's payslip.
- Expect: the UI offers no Re-run on an approved run; even forcing a re-run through the API leaves Amir's approved payslip **exactly** as it was (same net, still Approved).

#### ADM-PAY-07 — Late joiner after approval
- [ ] Pass
- Account: Sarah
- Pre: run is Approved; one employee had no salary and was skipped
- Steps: 1. Give that employee a salary. 2. Open Payroll.
- Expect: record the behaviour — there is no in-app way to generate a payslip for them in an already-approved period (Re-run is hidden). Confirm with the owner that this is acceptable for the release.

#### ADM-PAY-08 — Mark as paid
- [ ] Pass
- Account: Sarah
- Pre: Approved
- Steps: 1. Tap **Mark as paid** → **Cancel**. 2. Tap **Mark as paid** → **Mark as paid**.
- Expect: dialog "Mark as paid?" — "Confirm that RM <net> has been disbursed to staff. This can't be undone."; after confirming the chip is **Paid** (sage) and the buttons are replaced by a green "Paid · <pay date>" banner. No further lifecycle actions. Staff payslips now show the Paid pill.

#### ADM-PAY-09 — Fresh company with no salaries
- [ ] Pass
- Account: owner from AUTH-17
- Pre: no employee has a salary
- Steps: 1. Payroll → **Run payroll**.
- Expect: the user is told why nothing was generated ("No payroll generated — no active employees have a monthly salary set for this period."). A button that silently returns to the same empty card is a fail to log.

#### ADM-PAY-10 — Owner can run payroll too
- [ ] Pass
- Account: Owner
- Pre: any state
- Steps: 1. Open Payroll and perform any one lifecycle action.
- Expect: identical behaviour to HR Admin.

#### ADM-PAY-11 — Month rollover
- [ ] Pass
- Account: Sarah
- Pre: last month's run is Paid; device date is in a new month
- Steps: 1. Open Payroll.
- Expect: subtitle is the new month with "No payroll run yet"; last month's payslips remain visible to staff. (There is no period picker — the admin screen only works on the current calendar month.)

### D.14 Reports & export (`app/admin/payroll-export.tsx`)

#### ADM-EXP-01 — Statutory contributions table
- [ ] Pass
- Account: Sarah
- Pre: a run exists for the period
- Steps: 1. Payroll → **Reports & export**. 2. Scroll the table sideways.
- Expect: title "Reports & export" with the period; section "Statutory contributions" with a generated-at label; columns Employee, EPF (emp), EPF (co), SOCSO (emp), SOCSO (co), EIS (emp), EIS (co), PCB, Net; one row per payslip with staff ID (or NRIC) under the name; a "Company total" row whose every column equals the sum of the rows above. Employee figures match each payslip.

#### ADM-EXP-02 — No run for the period
- [ ] Pass
- Account: owner from AUTH-17 (no run), reached via a run-less period
- Pre: no payslips
- Steps: 1. Open Reports & export. 2. Tap any **Export**.
- Expect: "No payslips to report — This period has no persisted payslips yet. Run payroll first, then come back to export."; an export attempt shows a red error line instead of sharing an empty file.

#### ADM-EXP-03 — Contribution summary CSV
- [ ] Pass
- Account: Sarah
- Pre: run exists
- Steps: 1. Tap **Export** on "Contribution summary". 2. Save / AirDrop the file and open it in a spreadsheet.
- Expect: button shows "Preparing…" and all four Export buttons disable; share sheet titled "Payroll export"; a `.csv` named with the period; header row + one row per employee; amounts equal the on-screen table; names containing commas stay in one cell.

#### ADM-EXP-04 — Bank payment CSV
- [ ] Pass
- Account: Sarah
- Pre: ADM-EMP-26 done for at least one employee
- Steps: 1. Export "Bank payment". 2. Open it.
- Expect: names, bank name, account number and net pay; employees without bank details have **blank** bank cells; the net column sums to the run's Net disbursement.

#### ADM-EXP-05 — Full payroll CSV
- [ ] Pass
- Account: Sarah
- Pre: run exists
- Steps: 1. Export "Full payroll". 2. Open it.
- Expect: every payslip line with basic, overtime, claims, gross, each deduction and net — matching the Breakdown list.

#### ADM-EXP-06 — CP39 text file
- [ ] Pass
- Account: Sarah
- Pre: run exists
- Steps: 1. Read the caption under the CP39 row. 2. Export "CP39 (PCB)".
- Expect: caption "Best-effort LHDN CP39 — review against the latest format before submission."; a `cp39-<period>.txt` file with a header line then one line per employee; PCB amounts match the table.

#### ADM-EXP-07 — Cancel and retry
- [ ] Pass
- Account: Sarah
- Pre: on Reports & export
- Steps: 1. Start an export and dismiss the share sheet. 2. Export the same file again. 3. Turn on Airplane mode and export.
- Expect: dismissing returns to the screen with buttons re-enabled; the second export works (stale file is overwritten); offline shows a red error line, no crash.

### D.15 Scheduling (`app/admin/schedule.tsx`, `shift-assign.tsx`)

#### ADM-SCH-01 — Week view
- [ ] Pass
- Account: Sarah
- Pre: Dashboard → **Week schedule**
- Steps: 1. Read the header. 2. Tap each day in the day selector. 3. Scroll the shift list.
- Expect: title "Scheduling", subtitle "Week of <label>"; seven day chips; the selected one is coral; the sub-row shows "<Weekday>, <day>" and "<n> on shift" matching the number of cards; day selector and sub-row stay pinned while only the list scrolls. Each card: name, department, shift chip and time label.

#### ADM-SCH-02 — Empty day
- [ ] Pass
- Account: Sarah
- Pre: a day with no shifts
- Steps: 1. Select it. 2. Tap **Assign shift** in the empty state.
- Expect: "No shifts for <Weekday> — Nobody is scheduled this day yet."; the action opens "Assign shift".

#### ADM-SCH-03 — Assign a shift
- [ ] Pass
- Account: Sarah
- Pre: Scheduling → coral **+**
- Steps: 1. **Employee**: pick Amir. 2. **Date**: a day in the current week. 3. **Shift**: Evening. 4. Tap **Assign**.
- Expect: "Assigning…", returns to Scheduling; selecting that day shows Amir with an "Evening" chip; "<n> on shift" increased.

#### ADM-SCH-04 — Validation and re-assign
- [ ] Pass
- Account: Sarah
- Pre: on Assign shift
- Steps: 1. Tap **Assign** with no employee chosen. 2. Assign Amir **Remote** on the same date as ADM-SCH-03.
- Expect: 1 → "Pick an employee."; 2 → Amir's card for that day now shows Remote — one card, not two.

#### ADM-SCH-05 — Off shift and dates outside this week
- [ ] Pass
- Account: Sarah
- Pre: on Assign shift
- Steps: 1. Assign Amir **Off** for a day this week. 2. Assign a shift dated next week.
- Expect: note how "Off" is shown for that day and whether "on shift" excludes it; the next-week shift saves without error but cannot be seen (the screen only shows the current week — no week navigation). Log both observations.

### D.16 Company settings (`app/admin/company-settings.tsx`)

#### ADM-CO-01 — Company profile
- [ ] Pass
- Account: Sarah
- Pre: Profile tab → **Company settings**
- Steps: 1. Edit **Company name**, **Registration no. (SSM)**, **Employer EPF no.**, **Employer SOCSO no.**, **Email**, **Phone**, **Address**. 2. Tap **Save changes**.
- Expect: "Saving…", then the screen closes back to Profile; the Profile company chip shows the new name; re-opening shows all seven values kept. Restore the original name.

#### ADM-CO-02 — Company profile validation
- [ ] Pass
- Account: Sarah
- Pre: on Company settings
- Steps: 1. Clear **Company name** and save. 2. Enter `abc` as **Email** and save.
- Expect: a company can never end up with a blank name (currently a blank name is silently ignored and the old name kept — the form closes as if it saved; log whether a visible "name is required" message is wanted). An invalid email should be refused with a red message; if it is accepted, log it (the API does not validate this field).

#### ADM-CO-03 — Payroll & leave defaults
- [ ] Pass
- Account: Sarah
- Pre: on Company settings → "Payroll & leave defaults"
- Steps: 1. **Default pay basis**: Monthly. 2. **Default working days**: add Sat. 3. **Default hours per day**: `7.5`. 4. Tap **Save defaults**.
- Expect: the caption under the toggles updates live (e.g. "Mon, Tue, Wed, Thu, Fri, Sat"); "Defaults saved." appears and the screen stays open; re-opening shows the values kept. An employee with **no** personal override now shows the new schedule on their Compensation screen; an employee with an override is unchanged. Restore Mon–Fri / 8.

#### ADM-CO-04 — Defaults validation
- [ ] Pass
- Account: Sarah
- Pre: on defaults
- Steps: 1. Hours `0`. 2. Hours blank. 3. Hours `30`.
- Expect: 0 / blank → "Enter valid hours per day."; 30 → a server validation error (allowed range 0.5–24). Nothing saved.

#### ADM-CO-05 — Leave year start month
- [ ] Pass
- Account: Sarah, then Amir
- Pre: note Amir's balances
- Steps: 1. **Leave year starts**: Apr. 2. **Save defaults**. 3. Amir opens Leave. 4. Sarah opens Amir → Leave entitlement and reads the caption year.
- Expect: helper "January = calendar year. Entitlement, accrual and carry-forward all follow this month."; balances are now for the leave year containing today (named after the year it starts in — e.g. in October 2026 with an April start, "2026 leave year"); a monthly-accrual type recalculates its elapsed months from April. Restore to Jan.

#### ADM-CO-06 — Manager / employee cannot change settings
- [ ] Pass
- Account: Nadia (Swagger)
- Pre: Nadia's token
- Steps: 1. `PATCH /api/admin/company-settings`. 2. `PATCH /api/companies/me`.
- Expect: 403 for both; there is no settings entry anywhere in the staff app.

### D.17 Leave types manager (`company-settings.tsx`, `leave-type-edit.tsx`)

#### ADM-LT-01 — Create a paid leave type
- [ ] Pass
- Account: Sarah
- Pre: Company settings → "Leave types" → **Add leave type**
- Steps: 1. **Name**: "Compassionate Leave". 2. Leave **Code** blank (placeholder suggests `COMPASSIONATE_LEAVE`). 3. **Payment**: Paid. 4. **Default entitlement (days / year)**: `3`. 5. **Accrual**: Fixed annual. 6. **Carry-forward cap (days)**: blank. 7. Pick a **Colour**. 8. Tap **Create leave type**.
- Expect: title "New leave type"; returns to Company settings where the row shows "Compassionate Leave · 3 days/yr" with a **Paid** chip in the chosen colour. Staff see a new tile (3/3) and a new chip in Apply for leave.

#### ADM-LT-02 — Create an unpaid type
- [ ] Pass
- Account: Sarah
- Pre: on New leave type
- Steps: 1. Name "Study Leave (Unpaid)". 2. **Payment**: Unpaid (deducts salary). 3. Entitlement `0`, **Accrual**: None. 4. Create.
- Expect: selecting Unpaid shows "Days taken under this type are deducted from salary at the employee's derived daily rate."; the row has an amber **Unpaid** chip; staff get the unpaid banner when choosing it and **no** balance tile for it.

#### ADM-LT-03 — Validation
- [ ] Pass
- Account: Sarah
- Pre: on New leave type
- Steps: 1. Save with no name. 2. Entitlement `2.5`. 3. Entitlement `-1`. 4. Carry-forward `-1`. 5. Name made only of symbols (e.g. `***`) with Code blank.
- Expect: "Give the leave type a name." / "Enter a valid number of entitlement days." (twice) / "Enter a valid carry-forward cap (0 or more days)." / "Enter a code (e.g. ANNUAL)."

#### ADM-LT-04 — Duplicate code
- [ ] Pass
- Account: Sarah
- Pre: on New leave type
- Steps: 1. Name "Annual 2", **Code** `annual` (typed lowercase). 2. Create.
- Expect: the code field upper-cases as you type; red message "A leave type with code 'ANNUAL' already exists".

#### ADM-LT-05 — Edit a type: accrual and carry-forward
- [ ] Pass
- Account: Sarah
- Pre: Company settings → tap **Annual Leave**
- Steps: 1. Confirm title "Edit leave type" and that **Code** is read-only text. 2. **Accrual**: Monthly accrual. 3. **Carry-forward cap (days)**: `5`. 4. **Save changes**.
- Expect: helper "0 = unused days are forfeited at year end."; saved; staff tiles follow LEAVE rules in F.6 (accrued so far = entitlement × elapsed months ÷ 12). Fractional caps such as `2.5` are accepted. Restore to Fixed annual / 0 after the leave checks.

#### ADM-LT-06 — Hide and restore a type
- [ ] Pass
- Account: Sarah
- Pre: edit Emergency Leave
- Steps: 1. **Visibility**: Hidden → **Save changes**. 2. Check the list row. 3. Check a staff account (STF-LVE-19). 4. Set back to Active.
- Expect: row reads "5 days/yr · hidden"; staff can no longer choose it; existing requests and history are intact; restoring brings it back. (The Visibility control only exists when editing, not when creating.)

#### ADM-LT-07 — Changing paid ↔ unpaid affects the next payroll run
- [ ] Pass
- Account: Sarah
- Pre: an approved leave of a paid type in the current month; draft payroll
- Steps: 1. Edit that type to **Unpaid** → save. 2. Re-run payroll. 3. Set back to Paid and re-run.
- Expect: with Unpaid the employee's **Basic** in the Breakdown drops by days × daily rate (and their payslip shows the amber unpaid-leave note); back to Paid restores the full basic. Note whether an "Unpaid" figure appears in the admin Breakdown card — the screen has a slot for it but it has not been seen populated.

### D.18 Work locations (`app/admin/work-locations.tsx`, `work-location-edit.tsx`)

#### ADM-LOC-01 — Create a site with "Use my current location"
- [ ] Pass
- Account: Sarah
- Pre: Profile → **Work locations** (first time: "No work locations yet — Add your first site to enable geofenced clock-in.")
- Steps: 1. Tap **Add work location**. 2. **Name**: "QA Office". 3. Tap **Use my current location** and allow location. 4. Leave **Radius (metres)** at 100. 5. **Status**: Active. 6. Tap **Create work location**.
- Expect: the button shows "Getting location…", then the card shows "<lat>, <lng>" to 6 decimals with "Captured pin" and the button becomes **Update to current location**; after create, the list shows "QA Office · 100 m radius" with an **Active** chip.

#### ADM-LOC-02 — Radius below 50 m rejected
- [ ] Pass
- Account: Sarah
- Pre: on New work location with a name and captured pin
- Steps: 1. Radius `49` → save. 2. Radius `0`. 3. Radius blank. 4. Radius `75.5`. 5. Radius `50`.
- Expect: 1–4 → "Radius must be at least 50 m."; 50 is accepted. The helper text explains "GPS is only accurate to about 20–50 m, so keep the radius at 50 m or more (default 100 m) to avoid locking staff out."

#### ADM-LOC-03 — Name and coordinates required
- [ ] Pass
- Account: Sarah
- Pre: on New work location
- Steps: 1. Save with no name. 2. Add a name but do not capture a pin; save.
- Expect: "Give the work location a name." / "Capture the site coordinates with "Use my current location"."

#### ADM-LOC-04 — Location permission denied / no fix
- [ ] Pass
- Account: Sarah
- Pre: location denied for the app; then granted but unavailable
- Steps: 1. Tap **Use my current location** in each state.
- Expect: denied → "Location permission denied. Enable location for Teamora in Settings, then try again."; no fix → "Could not get your current location. Move to an open area and try again."; the button re-enables both times.

#### ADM-LOC-05 — Edit a site and update its pin
- [ ] Pass
- Account: Sarah
- Pre: tap "QA Office" in the list
- Steps: 1. Confirm the form is pre-filled (name, pin, radius, status). 2. Change the name and radius to `150`. 3. Move (or change simulated location) and tap **Update to current location**. 4. **Save changes**.
- Expect: title "Edit work location"; list shows the new name and "150 m radius"; an assigned employee's clock-in is now judged against the new pin and radius.

#### ADM-LOC-06 — Radius boundary at clock-in
- [ ] Pass
- Account: Sarah + an assigned employee (simulator location)
- Pre: site radius 100 m
- Steps: 1. Simulate a point ~80 m from the pin → clock in. 2. Next day (or another employee) simulate ~130 m → clock in.
- Expect: ~80 m succeeds; ~130 m is refused with the distance message quoting ~130 m and "within 100 m".

#### ADM-LOC-07 — Deactivate a site
- [ ] Pass
- Account: Sarah
- Pre: a site with an assigned employee
- Steps: 1. Edit → **Status**: Inactive → save. 2. Open an unassigned employee's Employment section. 3. Check the assigned employee (STF-CLK-09, ADM-EMP-18).
- Expect: list row reads "… · inactive" with a grey **Inactive** chip; the site is no longer offered for new assignments; the already-assigned employee clocks in anywhere.

#### ADM-LOC-08 — Manager cannot manage sites
- [ ] Pass
- Account: Nadia (Swagger)
- Pre: Nadia's token
- Steps: 1. `POST /api/admin/work-locations`.
- Expect: 403.

### D.19 Company calendar (`app/admin/calendar-events.tsx`, `calendar-event-edit.tsx`)

#### ADM-CAL-01 — Month stepper and empty month
- [ ] Pass
- Account: Sarah
- Pre: Profile → **Company calendar**
- Steps: 1. Read the intro. 2. Step forward to an empty month and back with the chevrons.
- Expect: intro "Public holidays, company events and townhalls. Everyone sees these on their calendar, and holidays are paid in payroll."; the month label changes each tap (December ↔ January rolls the year); an empty month shows "Nothing in <Month Year>" with **Add event**.

#### ADM-CAL-02 — Create a public holiday
- [ ] Pass
- Account: Sarah
- Pre: on Company calendar for the current month
- Steps: 1. Tap **Add event**. 2. **Title**: "QA Public Holiday". 3. **Date**: a coming weekday. 4. **Type**: Holiday. 5. Tap **Create event**.
- Expect: title "New event"; the date defaults to the 1st of the month being viewed; selecting Holiday shows "Public holidays are paid and are excluded from unpaid-leave deductions in payroll."; the list row shows the day number + weekday, the title and a "Holiday" chip. Staff calendars show it (STF-CAL-03) and leave on that date is refused (STF-LVE-13).

#### ADM-CAL-03 — Create an event with a time label
- [ ] Pass
- Account: Sarah
- Pre: on New event
- Steps: 1. Title "QA Team Lunch", Type **Event**, **Time label (optional)** "12:30 PM – 2:00 PM". 2. Create.
- Expect: row shows an "Event" chip and the time label; no holiday note is shown for this type.

#### ADM-CAL-04 — Create a townhall
- [ ] Pass
- Account: Sarah
- Pre: on New event
- Steps: 1. Title "QA Townhall", Type **Townhall**. 2. Create.
- Expect: row shows a "Townhall" chip; staff calendar shows it in the Town hall colour.

#### ADM-CAL-05 — Validation
- [ ] Pass
- Account: Sarah
- Pre: on New event
- Steps: 1. Save with a blank title. 2. Title of 256+ characters. 3. Time label of 65+ characters.
- Expect: "Give the event a title." / "Title is too long (max 255 characters)." / "Time label is too long (max 64 characters)."

#### ADM-CAL-06 — Edit an event (incl. clearing the time label and moving month)
- [ ] Pass
- Account: Sarah
- Pre: tap "QA Team Lunch"
- Steps: 1. Change the title. 2. Clear the **Time label**. 3. Move the **Date** into next month. 4. **Save changes**.
- Expect: title "Edit event"; the event leaves the current month's list and appears under next month with the new title and **no** time label.

#### ADM-CAL-07 — Delete an event
- [ ] Pass
- Account: Sarah
- Pre: open the holiday from ADM-CAL-02
- Steps: 1. Tap **Delete event** → **Cancel**. 2. Tap **Delete event** → **Delete**.
- Expect: dialog "Delete this event?" — ""QA Public Holiday" will be removed from everyone's calendar. It will no longer be paid as a public holiday in payroll." (the payroll sentence only for Holiday type); Cancel keeps it; Delete removes it from the admin list and the staff calendar, and staff can apply for leave on that date again.

#### ADM-CAL-08 — Manager is read-only
- [ ] Pass
- Account: Nadia (Swagger)
- Pre: Nadia's token
- Steps: 1. `GET /api/admin/calendar/events`. 2. `POST /api/admin/calendar/events`.
- Expect: GET 200, POST 403.

### D.20 Admin profile (`app/admin/(tabs)/profile.tsx`)

#### ADM-PRO-01 — Identity and stats
- [ ] Pass
- Account: Sarah
- Pre: Profile tab
- Steps: 1. Read the card and tiles.
- Expect: "Sarah Lim", "HR Manager", company chip with the company name; tiles **Role** "HR Admin", **Department** "People", **Staff ID** "EMP-001".

#### ADM-PRO-02 — Menu rows navigate or do not exist
- [ ] Pass
- Account: Sarah
- Pre: on Profile
- Steps: 1. Tap **Company settings**, **Work locations**, **Company calendar**, **Team members**, **Change password** — returning each time. 2. Tap any remaining row and the header pencil.
- Expect: the five rows open Company settings, Work locations, Company calendar, the Staff tab and Change password (AUTH-26). Rows with no destination ("Personal information", "App settings", "Privacy & security") and a pencil that does nothing must **not be shown** — no chevron row may be a dead end.

#### ADM-PRO-03 — Log out
- [ ] Pass
- Account: Sarah
- Pre: on Profile
- Steps: 1. Tap **Log out**.
- Expect: Onboarding (see AUTH-24).

#### ADM-PRO-04 — Admin's own HR needs
- [ ] Pass
- Account: Sarah
- Pre: signed in
- Steps: 1. Look for a way to clock in, apply for leave, submit a claim, view her own payslip and read notifications.
- Expect: record what is and is not reachable. The admin app currently has **no** route to the staff self-service screens or to Notifications, even though admins receive approval notifications and payslips. Confirm with the owner whether this blocks the release.

### D.21 Admin tab bar

#### ADM-NAV-01 — Pill tab bar
- [ ] Pass
- Account: Sarah
- Pre: none
- Steps: 1. Tap Home, Staff, Approve, Payroll, Profile. 2. Scroll each to the bottom.
- Expect: active tab expands to icon + label; content clears the floating bar on every tab; pushed screens (Live, Scheduling, hub and its sections, settings, exports, editors) have a back button and no tab bar.

---

## E. Cross-cutting

### E.1 Role routing

#### X-ROLE-01 — Each role lands in the right app
- [ ] Pass
- Account: owner@, sarah@, nadia@, amir@ (one after another)
- Pre: signed out between each
- Steps: 1. Sign in with each account. 2. Force-quit and relaunch while signed in.
- Expect: OWNER and HR_ADMIN → admin Dashboard; MANAGER and EMPLOYEE → staff Home — both right after login and after a session restore.

#### X-ROLE-02 — A role change takes effect on next sign-in
- [ ] Pass
- Account: Sarah + the QA employee
- Pre: QA employee signed in on a second device (staff app)
- Steps: 1. Sarah changes the QA employee to HR Admin. 2. On the second device, use the app, then log out and sign in again.
- Expect: before re-login, admin-only calls are still refused or the staff app keeps working without a crash; after re-login the user lands in the admin app. Change the role back and confirm they return to the staff app.

#### X-ROLE-03 — Staff cannot open admin routes
- [ ] Pass
- Account: Amir
- Pre: signed in (dev build / Expo Go where a deep link can be opened)
- Steps: 1. Open the deep link to `/admin/dashboard` and `/admin/staff` (app scheme from `app.config.ts`). 2. Look at what renders.
- Expect: no company data is shown — the admin APIs answer 403, so the screens show the error state ("Couldn't load this…") at worst. No staff list, salaries or payroll figures leak. (Security is server-side; a redirect back to Home would be nicer — log what happens.)

#### X-ROLE-04 — Employee API permissions (spot check, Swagger with Amir's token)
- [ ] Pass
- Account: Amir
- Pre: token from `POST /api/auth/login`
- Steps: 1. `GET /api/admin/dashboard`. 2. `GET /api/employees`. 3. `POST /api/admin/leave/requests/{id}/approve`. 4. Any call with no token.
- Expect: 403, 403, 403, and **401** without a token.

### E.2 Tenant isolation

#### X-TEN-01 — Second company sees only its own people
- [ ] Pass
- Account: `admin@nusantara.com`
- Pre: Lumi has pending requests, clock-ins and a payroll run
- Steps: 1. Sign in. 2. Visit Dashboard, Staff, Approve (all three tabs), Payroll, Live attendance, Week schedule.
- Expect: company chip "Nusantara Tech Sdn Bhd"; Staff lists only Nusantara employees (Daniel, Budi…); **zero** Lumi names, requests, attendance rows, shifts or payroll figures anywhere.

#### X-TEN-02 — Settings do not bleed across companies
- [ ] Pass
- Account: `admin@nusantara.com`, then Sarah
- Pre: Lumi has custom leave types, a work location and a holiday from earlier cases
- Steps: 1. Nusantara admin opens Company settings (leave types), Work locations and Company calendar. 2. Nusantara admin adds a holiday and a leave type. 3. Sarah checks Lumi's lists.
- Expect: Nusantara sees only its own default leave types, no Lumi site, no Lumi events; the items added by Nusantara never appear for Lumi. Reporting manager and Work location pickers list only the caller's company.

#### X-TEN-03 — Staff across companies
- [ ] Pass
- Account: `budi@nusantara.com`
- Pre: Lumi holiday / event exists this month
- Steps: 1. Open Calendar, Leave → Apply for leave, Notifications.
- Expect: no Lumi events on the calendar; leave types are Nusantara's; no Lumi notifications.

#### X-TEN-04 — Same email cannot exist in two companies
- [ ] Pass
- Account: `admin@nusantara.com`
- Pre: none
- Steps: 1. Add employee with `amir@lumi.com`.
- Expect: "An account with this email already exists".

### E.3 Session expiry and refresh

#### X-SES-01 — Silent refresh when the access token expires
- [ ] Pass
- Account: Amir
- Pre: signed in; wait for the access token to expire (or shorten its lifetime in `backend/.env` for the test and rebuild)
- Steps: 1. After expiry, open Leave and submit a claim.
- Expect: both work with no visible interruption — the app refreshes the token once and retries. No duplicate claim is created.

#### X-SES-02 — Revoked / expired refresh token signs the user out
- [ ] Pass
- Account: Amir on device A
- Pre: signed in on A; revoke the session (log out the same session through Swagger `POST /api/auth/logout` with A's refresh token, or reset the DB with `down -v`) and let the access token expire
- Steps: 1. On A, open any data screen.
- Expect: the user is returned to the signed-out flow (Onboarding / Login) — not left on a screen full of error boxes; signing in again works.

#### X-SES-03 — Relaunch with no network keeps the session
- [ ] Pass
- Account: Amir
- Pre: signed in; force-quit; enable Airplane mode
- Steps: 1. Launch the app. 2. Disable Airplane mode.
- Expect: the user should **not** be logged out just because the network was down at launch; screens show retryable error states and recover when the connection returns. (Current code clears the session if the launch-time `/api/auth/me` call fails for any reason — log the actual behaviour.)

#### X-SES-04 — Deactivated or deleted account
- [ ] Pass
- Account: QA employee
- Pre: signed in on device B; reset the database so the account no longer exists
- Steps: 1. Use the app on device B.
- Expect: clean sign-out to Onboarding; no crash loop.

### E.4 Offline, error, empty and loading states

#### X-NET-01 — Every data screen has a loading state
- [ ] Pass
- Account: Amir, then Sarah
- Pre: throttle the network (Network Link Conditioner "Very Bad Network" / Android emulator throttling)
- Steps: 1. Open each tab and pushed list screen once.
- Expect: a coral spinner occupies the data area (no layout jump when content arrives); headers and back buttons are usable immediately; no "undefined", "NaN" or "RM —.—" flashes.

#### X-NET-02 — Every data screen has a retryable error state
- [ ] Pass
- Account: Amir, then Sarah
- Pre: Airplane mode with a valid session
- Steps: 1. Staff: Home, Attendance, Calendar, Payroll, Profile, Leave, Claims, Log overtime, Notifications, Approvals (as Nadia). 2. Admin: Dashboard, Staff, Approve, Payroll, Profile, Live attendance, Scheduling, Employee hub + each section, Company settings (three sections), Work locations, Company calendar, Reports & export. 3. Restore the network and tap **Try again** on each.
- Expect: each shows "Couldn't load this. Check your connection." with **Try again**; retry loads real data. No blank white screen and no infinite spinner.

#### X-NET-03 — Every form keeps input when the save fails
- [ ] Pass
- Account: Amir, then Sarah
- Pre: fill a form, then enable Airplane mode
- Steps: 1. Submit: Apply for leave, New claim, Log overtime, Add employee, each hub section, Leave type, Work location, Calendar event, Assign shift, Company profile, Payroll defaults.
- Expect: a red message ("Something went wrong. Please try again." / "Could not save changes. Check your connection." / similar); the form stays open with everything typed intact; the button re-enables; submitting again online saves **once**.

#### X-NET-04 — Double-tap protection
- [ ] Pass
- Account: Amir, Sarah
- Pre: throttled network
- Steps: 1. Tap rapidly several times on: **Submit request**, **Submit claim**, **Submit overtime**, **Verify & Clock In**, **Add employee**, **Run payroll**, **Approve** on an approval card.
- Expect: exactly one record / one action each; the button shows its pending label and is disabled while in flight.

#### X-NET-05 — Empty states are honest (fresh company)
- [ ] Pass
- Account: owner from AUTH-17 and one employee added to that company
- Pre: brand-new company
- Steps: 1. Walk every screen in both apps.
- Expect: friendly empty states with a next action where one exists; **no sample people, amounts, counts, map pins, office names or bank accounts** anywhere.

#### X-NET-06 — Server validation errors are shown verbatim and in plain language
- [ ] Pass
- Account: any
- Pre: trigger three different server-side refusals (e.g. duplicate email, leave on a holiday, out-of-range clock-in)
- Steps: 1. Read each message.
- Expect: the server's sentence is displayed as-is, readable by a non-technical user; never "Request failed (400)", a stack trace or raw JSON.

### E.5 Keyboard handling

#### X-KBD-01 — Focused field stays above the keyboard
- [ ] Pass
- Account: Sarah (small phone)
- Pre: none
- Steps: 1. On Statutory & bank, Company settings and New claim, tap the lowest text field. 2. Move through fields from top to bottom.
- Expect: the focused field scrolls into view above the keyboard every time; the Save button can be reached by scrolling without dismissing the keyboard first.

#### X-KBD-02 — Dismissing the keyboard
- [ ] Pass
- Account: any
- Pre: keyboard open on any form
- Steps: 1. Tap outside the fields. 2. Re-open and drag the content down.
- Expect: both gestures dismiss the keyboard; a tap on a button while the keyboard is open triggers the button on the **first** tap.

#### X-KBD-03 — Correct keyboard per field
- [ ] Pass
- Account: any
- Pre: none
- Steps: 1. Focus: Work email (Login, Register, Add employee), Amount (RM), Hours (leave + overtime), Monthly salary, Hours per day, Radius (metres), Number of children, Bank account no., Phone.
- Expect: email keyboard for emails (no auto-capitalise); decimal pad for amounts / hours / salary; number pad for radius, children, bank account and entitlement days; phone pad for Phone.

#### X-KBD-04 — Android hardware back and iOS swipe-back
- [ ] Pass
- Account: any
- Pre: on a pushed form with the keyboard open
- Steps: 1. Android: press back twice. 2. iOS: swipe from the left edge.
- Expect: Android first closes the keyboard, then the screen; iOS swipe returns to the previous screen; no accidental submit.

### E.6 Accessibility labels (VoiceOver / TalkBack)

#### X-A11Y-01 — Core controls are announced
- [ ] Pass
- Account: Amir, Sarah
- Pre: screen reader on
- Steps: 1. Swipe through: the tab bar, a screen header back button, a primary button, a SelectChips group, a DateField, a TimeField, the Working days toggles, the Company calendar month stepper.
- Expect: tab items announce their label as tabs; back announces "Go back"; buttons announce their visible label; chips announce the option name as radio buttons; date / time fields announce "<label>, <value>"; weekday toggles announce the full day name as switches; the stepper announces "Previous month" / "Next month".

#### X-A11Y-02 — Icon-only controls have a spoken name
- [ ] Pass
- Account: Amir, Sarah
- Pre: screen reader on
- Steps: 1. Focus: the coral **+** on Staff and Scheduling, the Clock In screen's back chevron, the search field's clear ✕, the Calendar month chevrons (staff), Approve / **Decline** on an approval card, the leave-type **Colour** swatches, **Delete event**, the Live board selfie thumbnail.
- Expect: each announces a meaningful name and role. Log every control that is silent or announced only as "button" — several icon-only controls currently have no label.

#### X-A11Y-03 — Large text and contrast
- [ ] Pass
- Account: Amir, Sarah
- Pre: OS font size at the largest standard setting
- Steps: 1. Open Home, Leave, Payslip, Dashboard, Payroll, the Employee hub.
- Expect: no clipped descenders, no overlapping text, money columns still aligned, buttons still tappable (≥ 44 pt); text on the espresso hero and on tinted chips remains readable.

### E.7 Data-integrity regressions

#### X-DATA-01 — Partial employee update never wipes other sections
- [ ] Pass
- Account: Sarah
- Pre: an employee with a manager, work location, salary, statutory details, tax profile, join date and an entitlement override
- Steps: 1. Save each of the five hub sections once, changing one field per section. 2. After the last save, open every section.
- Expect: every value that was not edited is exactly as before — in particular **reporting manager and work location survive** saves from Profile, Compensation, Statutory & bank and Leave entitlement (see ADM-EMP-11).

#### X-DATA-02 — Leave is deducted exactly once
- [ ] Pass
- Account: Amir, Nadia, Sarah
- Pre: note Amir's Annual balance
- Steps: 1. Amir applies 1 day. 2. Nadia approves. 3. Sarah tries to act on it again (stale screen). 4. Compare the balance on Amir's Leave tile, Home summary, Profile "Leave left" and the admin Leave entitlement "used" line.
- Expect: balance reduced by exactly 1 in all four places.

#### X-DATA-03 — Balance, request and payslip agree on a partial unpaid day
- [ ] Pass
- Account: Amir, Nadia, Sarah
- Pre: Amir applies Unpaid Leave, Hours = 2 (8 h day), approved; payroll draft re-run
- Steps: 1. Compare the request's duration label, the approver card's chip, and the payslip unpaid note.
- Expect: all three describe the same slice: 2 hours = 0.25 day, deduction = 0.25 × daily rate.

#### X-DATA-04 — Exactly one owner at all times
- [ ] Pass
- Account: Owner, Sarah
- Pre: none
- Steps: 1. Try to give a second person the Owner role from Add employee and from Employment. 2. Do an ownership transfer and inspect the Staff list.
- Expect: Owner is never offered as a role choice; after a transfer there is still exactly one Owner chip in the company.

#### X-DATA-05 — Money and day figures are formatted consistently
- [ ] Pass
- Account: Amir, Sarah
- Pre: data with fractions (0.5 / 0.25 day leave, sen amounts)
- Steps: 1. Scan every screen that shows money or days.
- Expect: money always `#,##0.00` with "RM"; days with at most 2 decimals and no trailing zeros ("11.5", "0.25", "16"); never floating-point noise (e.g. 11.499999 or 22.750000000001).

#### X-DATA-06 — Time zone correctness
- [ ] Pass
- Account: Amir
- Pre: device set to Asia/Kuala_Lumpur, then to another zone (e.g. London)
- Steps: 1. Clock in. 2. Read "In at …" on Home, the Attendance row, and the admin Live board time. 3. Apply for leave on a specific date and read it back.
- Expect: with the device in Malaysia time everything agrees with the wall clock. With another zone, the leave date is still the date that was picked (no off-by-one day) and the attendance "work date" follows Malaysia's calendar day; note any display differences.

---

## F. Payroll calculation scenarios (worked examples)

Run these against the **current month** — the admin Payroll screen always works
on the current calendar period. Use the draft run and **Re-run** between cases;
approve only at the end (PAY-12).

### F.0 Rules and reference numbers

| Rule | Formula |
| --- | --- |
| Daily rate | monthly salary ÷ **scheduled working days in that month** (per the employee's working-day mask; public holidays do **not** reduce the count) |
| Hourly rate | daily rate ÷ hours per day |
| Unpaid leave | deduction = day fraction × daily rate (full day 1.0, half day 0.5, hours ÷ hours-per-day), rounded to the sen; paid basic = salary − deduction |
| Paid leave / public holiday | never deducts |
| Absence with no leave record | does not deduct (Monthly basis) |
| Overtime | hours × (monthly salary ÷ 26 ÷ 8 × 1.5) — uses the **full** monthly salary, independent of the working-day mask |
| Claims | approved claims dated in the month are added to gross; never part of any statutory base |
| EPF | employee 11 % of paid basic, **rounded up to the next ringgit**; employer 13 % (basic ≤ RM 5,000) or 12 % (above), also rounded up |
| SOCSO / EIS | on the midpoint of the RM 100 wage band containing paid basic (e.g. RM 4,000 → RM 3,950), capped at RM 5,950; SOCSO 0.5 % employee / 1.75 % employer; EIS 0.2 % each |
| PCB (est.) | annualise paid basic × 12; subtract EPF relief (min(EPF × 12, 4,000)) and reliefs (9,000 individual, + 4,000 if married with a non-working spouse, + 2,000 per child); apply YA2024 brackets; ÷ 12 |
| Net | gross (basic + OT + claims) − EPF − SOCSO − EIS − PCB |

Mon–Fri working days and rates for an RM 4,000 salary, 8 h/day:

| Month | Working days | Daily rate | Hourly rate |
| --- | --- | --- | --- |
| Oct 2026 | 22 | 181.82 | 22.73 |
| Nov 2026 | 21 | 190.48 | 23.81 |
| Dec 2026 | 23 | 173.91 | 21.74 |
| Jan 2027 | 21 | 190.48 | 23.81 |
| Feb 2027 | 20 | 200.00 | 25.00 |
| Mar 2027 | 23 | 173.91 | 21.74 |

Examples below marked *(22-day month)* use October 2026. In another month,
recompute with the table — the expected **method** is identical.

#### PAY-01 — Baseline: RM 4,000, single, no children, full month
- [ ] Pass
- Account: Sarah (check as the employee too)
- Pre: QA employee: salary 4000, Monthly, Mon–Fri, 8 h, tax profile Single / 0 children; no leave, OT or claims this month
- Steps: 1. Run / Re-run payroll. 2. Read the employee's Breakdown card and their staff Payslip. 3. Read the row in Reports & export.
- Expect: Basic 4,000.00 · Gross 4,000.00 · EPF 440.00 · SOCSO 19.75 · EIS 7.90 · PCB 50.00 · Total deductions 517.65 · **Net 3,482.35**. Export row: EPF (co) 520.00, SOCSO (co) 69.13, EIS (co) 7.90.

#### PAY-02 — Tax profile changes PCB only (Amir: married, spouse not working, 2 children)
- [ ] Pass
- Account: Sarah
- Pre: Amir, RM 4,000, full month, nothing else
- Steps: 1. Re-run. 2. Read Amir's line and payslip.
- Expect: EPF 440.00, SOCSO 19.75, EIS 7.90 (unchanged); **PCB 30.00**; Total deductions 497.65; **Net 3,502.35**.

#### PAY-03 — One full day of unpaid leave *(22-day month)*
- [ ] Pass
- Account: QA employee → Nadia / Sarah → Sarah
- Pre: PAY-01 state; the employee applies **Unpaid Leave**, Full day, one working day this month; approved
- Steps: 1. Re-run payroll. 2. Read the line and the employee's payslip.
- Expect: unpaid deduction 4,000 ÷ 22 = **181.82**; Basic **3,818.18**; EPF 420.00; SOCSO 19.25; EIS 7.70; PCB 44.55; Total deductions 491.50; **Net 3,326.68**. Payslip note: "Unpaid leave · 1 day" and "− RM 181.82 · RM 181.82/day — already reflected in basic salary." (For Amir's tax profile: PCB 24.55, Net 3,346.68.)
- Cross-check (20-day month, 2 unpaid days — pinned by the automated tests): deduction 400.00, Basic 3,600.00, EPF 396.00, SOCSO 17.75, EIS 7.10, PCB 38.00 (single), Net 3,141.15.

#### PAY-04 — Partial unpaid leave: half day and hours *(22-day month)*
- [ ] Pass
- Account: QA employee → approver → Sarah
- Pre: PAY-01 state (cancel out PAY-03 by using a different employee or a reset); test each sub-case on its own
- Steps: 1. Sub-case A: Unpaid Leave, **Half day**, approved, Re-run. 2. Sub-case B: Unpaid Leave, **Hours = 2**, approved, Re-run.
- Expect: A → deduction 0.5 × 181.8182 = **90.91**, Basic 3,909.09, EPF 430.00, SOCSO 19.75, EIS 7.90, PCB 47.27, Net 3,404.17; payslip note "0.5 days". B → deduction 0.25 × 181.8182 = **45.45**, Basic 3,954.55; payslip note "0.25 days".

#### PAY-05 — Approved overtime is paid; pending / declined is not
- [ ] Pass
- Account: Amir → Nadia → Sarah
- Pre: Amir full month; overtime 2.5 h dated this month approved (MGR-07); a second OT entry left pending and a third declined
- Steps: 1. Re-run. 2. Read Amir's line.
- Expect: OT rate 4,000 ÷ 26 ÷ 8 × 1.5 = 28.846…/h; OT pay 2.5 h = **72.12** (4 h would be 115.38); Gross 4,072.12; EPF / SOCSO / EIS / PCB **unchanged** from PAY-02 (497.65 total); **Net 3,574.47**. Pending and declined hours add nothing.

#### PAY-06 — Approved claims are reimbursed, not taxed
- [ ] Pass
- Account: Amir → Nadia → Sarah
- Pre: PAY-05 state; a claim of RM 85.50 dated this month approved; another claim dated **last month** approved; a third pending
- Steps: 1. Re-run. 2. Read Amir's line and payslip.
- Expect: Claims 85.50 (only the approved, this-month claim); Gross 4,157.62; deductions still 497.65; **Net 3,659.97**. Payslip shows "Claims reimbursed + RM 85.50" and "Overtime + RM 72.12".

#### PAY-07 — Paid leave and public holidays never deduct
- [ ] Pass
- Account: QA employee → approver → Sarah
- Pre: PAY-01 state; (a) approve 2 days of **Annual Leave** this month; (b) add a **Holiday** on a working day this month; (c) the employee simply does not clock in on several days
- Steps: 1. Re-run. 2. Read the line.
- Expect: Basic stays **4,000.00** and net stays 3,482.35 in all three situations; no unpaid note on the payslip.

#### PAY-08 — Unpaid leave spanning a public holiday and a weekend *(22-day month)*
- [ ] Pass
- Account: QA employee → approver → Sarah
- Pre: a Holiday on a Tuesday this month; the employee applies Unpaid Leave Full day **Friday → the following Wednesday** (6 calendar days containing Sat, Sun and the holiday)
- Steps: 1. Read the request's duration. 2. Approve and Re-run.
- Expect: the request counts **3 days** (Fri, Mon, Wed); deduction 3 × 181.8182 = **545.45**; Basic 3,454.55. The weekend and the holiday are neither charged to the balance nor deducted from pay.

#### PAY-09 — High earner: employer EPF 12 %, SOCSO/EIS ceiling (Wei Jie, RM 7,000, single)
- [ ] Pass
- Account: Sarah
- Pre: full month, nothing else
- Steps: 1. Read Wei Jie's line and export row.
- Expect: EPF 770.00 (employer 840.00 = 12 %); SOCSO 29.75; EIS 11.90 (both capped at the RM 5,950 band); PCB 324.17; Total deductions 1,135.82; **Net 5,864.18**.

#### PAY-10 — Lower earner (Faizal, RM 2,800, single)
- [ ] Pass
- Account: Sarah
- Pre: full month, nothing else
- Steps: 1. Read Faizal's line.
- Expect: EPF 308.00; SOCSO 13.75; EIS 5.50; PCB 14.76; Total deductions 342.01; **Net 2,457.99**.

#### PAY-11 — Schedule changes the daily rate
- [ ] Pass
- Account: Sarah
- Pre: QA employee RM 4,000; one approved unpaid full day on a day that is a working day under both schedules
- Steps: 1. Compensation → set working days to **every day** → save → Re-run. 2. Read the deduction. 3. Set back to Mon–Fri → Re-run.
- Expect: every-day schedule in a 31-day month → daily rate 4,000 ÷ 31 = 129.03, deduction **129.03**; back on Mon–Fri (22 days) → **181.82**. The Compensation preview shows the same daily rate as the payslip note.

#### PAY-12 — Re-run is idempotent; approved and paid payslips are locked
- [ ] Pass
- Account: Sarah
- Pre: a stable draft
- Steps: 1. Tap **Re-run** twice with no data changes and compare every figure. 2. **Approve payroll**. 3. Approve a new claim for Amir dated this month. 4. Force a re-run through the API (see ADM-PAY-06). 5. **Mark as paid**, then force a re-run again.
- Expect: step 1 — identical totals, same number of payslips; steps 4 and 5 — Amir's payslip amounts and status are **unchanged** (Approved, then Paid). The late claim is simply not in this month's pay.

#### PAY-13 — Totals reconcile across the three views
- [ ] Pass
- Account: Sarah
- Pre: a run with at least five payslips
- Steps: 1. Add up the per-employee nets in the Breakdown. 2. Compare with the hero, the totals card, the export "Company total" row, the Bank payment CSV total and the Dashboard "payroll due" tile. 3. Pick two employees and compare their Breakdown card with their own staff Payslip and PDF.
- Expect: the same net total to the sen in all five places; employee figures identical on the admin line, the staff screen and the PDF.

#### PAY-14 — Daily and Hourly pay basis
- [ ] Pass
- Account: Sarah
- Pre: QA employee RM 4,000, Mon–Fri, 8 h; in the month so far: 3 completed clocked-in days, 1 public holiday on a working day, no leave
- Steps: 1. Compensation → **Pay basis: Daily** → save → Re-run → read Basic. 2. **Pay basis: Hourly** → save → Re-run → read Basic.
- Expect: Daily → Basic = daily rate × (days present + paid-leave days + holidays on working days) = 181.8182 × 4 = **727.27** *(22-day month)*. Hourly → Basic = hourly rate × (hours actually worked + 8 h per paid-leave day / holiday). Confirm the figures follow these formulas and restore **Monthly** afterwards.

### F.6 Leave balance arithmetic

| Rule | Formula |
| --- | --- |
| Fixed annual | accrued = full entitlement from day one |
| Monthly accrual | accrued = entitlement × elapsed months ÷ 12 (the current month counts as elapsed), 2 dp |
| None | accrues nothing; no balance is tracked |
| Remaining | accrued + carried forward − used (can be negative) |
| Join-date proration | joined inside the leave year → entitlement × (12 − month index of join month) ÷ 12 |
| Carry-forward | min(max(last year's entitlement + carried − used, 0), the type's cap); cap 0 = forfeited |
| Leave year | named after the calendar year it starts in; a request is charged to the year its **start date** falls in |

#### LVC-01 — Monthly accrual
- [ ] Pass
- Account: Sarah, Amir
- Pre: Annual Leave = Monthly accrual, entitlement 16, leave year starts Jan; Amir used 4
- Steps: 1. Amir opens Leave (in October).
- Expect: "13.33 accrued so far" (16 × 10 ÷ 12); remaining **9.33** "/16". In another month: 16 × (month number) ÷ 12.

#### LVC-02 — Accrual None shows no balance
- [ ] Pass
- Account: Sarah, Amir
- Pre: a type with Accrual = None (e.g. Unpaid Leave)
- Steps: 1. Amir opens Leave and Apply for leave.
- Expect: no tile for the type, but it can still be chosen when applying.

#### LVC-03 — Join-date proration
- [ ] Pass
- Account: Sarah, a new employee
- Pre: leave year starts Jan; add a new employee and set **Joined** = 1 August of this year (Employment section)
- Steps: 1. The new employee opens Leave. 2. Sarah opens their Leave entitlement.
- Expect: Annual **6.67** (16 × 5 ÷ 12), Medical **5.83** (14 × 5 ÷ 12), Emergency **2.08** (5 × 5 ÷ 12). An employee whose join date is in an earlier leave year, or not recorded, gets the full entitlement. *(Watch closely: the hub loads leave balances as soon as it opens, before a join date can be entered — if the employee still shows 16 / 14 / 5 after the join date is saved, proration is not being applied and this case fails.)*

#### LVC-04 — Carry-forward with a cap
- [ ] Pass
- Account: Sarah, Amir
- Pre: needs a previous-leave-year balance. Set **Leave year starts** so that a new leave year begins this month or next (e.g. in October choose Nov, read balances once so last year's rows exist, then move the device/server date or wait); Annual cap = 5; Amir had 12 unused
- Steps: 1. In the new leave year, Amir opens Leave.
- Expect: Annual shows "5 carried over" and remaining = this year's accrued + 5 − used. With cap 0 nothing carries over. If a year boundary cannot be reproduced in the test window, mark this case "covered by `LeaveAccrualIT`" and verify only that the "carried over" line renders (ADM-EMP-30 context line).

#### LVC-05 — Leave booked into the next leave year
- [ ] Pass
- Account: Amir, Nadia
- Pre: leave year starts Jan
- Steps: 1. Amir applies Annual Leave for a working day in next January. 2. Nadia reads the "days left" on the card and approves. 3. Amir checks his current tiles.
- Expect: the approver card shows next year's availability; after approval **this year's** balance is unchanged (the day is charged to next year's entitlement).

#### LVC-06 — Changing a type's default entitlement
- [ ] Pass
- Account: Sarah, Amir, a newly added employee
- Pre: none
- Steps: 1. Edit Annual Leave default entitlement 16 → 18. 2. Check Amir's tile. 3. Add a new employee and check theirs.
- Expect: record the behaviour — existing employees' balances for the current leave year keep their stored entitlement (16) while newly provisioned balances use 18. Confirm with the owner that this is the intended rule and that the settings screen makes it clear.

---

## G. Device-only checks (physical device, preview build)

These cannot be verified in the iOS Simulator (no camera) or Expo Go (no remote
push). Run them on at least one iPhone and one Android phone. The receipt-camera
cases in B.5 (STF-CLM-10 → 15, 18, 19 step 4) need a physical device too.

#### DEV-01 — Selfie capture on clock-in
- [ ] Pass
- Account: Amir on a physical phone
- Pre: preview build; camera permission not yet decided; not clocked in today
- Steps: 1. Home → **Clock In**. 2. Allow the camera when prompted. 3. Check the round viewfinder shows the **front** camera live, with a sage dashed ring and the chip "Center your face in the frame". 4. Tap **Verify & Clock In**.
- Expect: button shows "Taking photo…" then "Verifying…"; clock-in completes in a few seconds; on a second device the admin Live board shows the selfie thumbnail, upright (not rotated / mirrored oddly), and the enlarged viewer shows the same photo.

#### DEV-02 — Camera permission denied, then granted in Settings
- [ ] Pass
- Account: another employee on the phone
- Pre: deny the camera prompt
- Steps: 1. Clock in (expect the no-photo path, STF-CLK-04). 2. Next day (or another account): enable the camera in system Settings, reopen Clock In.
- Expect: denied → clock-in still succeeds without a photo; after enabling → live preview returns and a photo is stored.

#### DEV-03 — Selfie upload size and speed
- [ ] Pass
- Account: staff on mobile data (not Wi-Fi)
- Pre: weak signal if possible
- Steps: 1. Clock in with a selfie.
- Expect: succeeds within ~10 s; never fails with "Photo too large (max 2 MB). Please retake."; if the photo cannot be taken the clock-in still goes through.

#### DEV-04 — Real GPS geofence, on site
- [ ] Pass
- Account: Sarah, then an assigned employee
- Pre: Sarah stands at the real site and creates the work location with **Use my current location** (radius 100); assigns the employee
- Steps: 1. Employee clocks in while standing inside the site, including indoors.
- Expect: first use shows the OS location prompt; clock-in succeeds; no false "out of range" indoors at 100 m. If it fails indoors, note the reported distance.

#### DEV-05 — Real GPS geofence, off site
- [ ] Pass
- Account: the assigned employee
- Pre: physically > 200 m from the pin
- Steps: 1. Tap **Verify & Clock In**. 2. Walk back inside the radius and retry.
- Expect: off site → "You're ~<N> m from <site>. Move within 100 m to clock in." with a plausible distance; on site → succeeds.

#### DEV-06 — Location services off / "While using" only / precise off
- [ ] Pass
- Account: the assigned employee
- Pre: toggle each OS setting in turn
- Steps: 1. Location Services off system-wide → clock in. 2. Permission "Ask next time" / "Only this time". 3. iOS Precise Location off.
- Expect: 1 → the "Could not get your location…" or "Location is needed…" message, never a hang; 2 → prompt appears and clock-in works; 3 → note whether approximate location still passes a 100 m radius (likely not) and that the message is understandable.

#### DEV-07 — Push notification delivery
- [ ] Pass
- Account: Amir on phone A (preview build), Nadia on phone B
- Pre: both allowed notifications at first sign-in
- Steps: 1. With Nadia's app in the background, Amir submits a leave request. 2. Nadia taps the banner. 3. With Amir's app closed, Nadia approves. 4. Amir taps the banner.
- Expect: Nadia receives "New leave request" within seconds; tapping opens Notifications with the item at the top; Amir receives "Leave approved"; tapping from a cold start lands on Notifications once the app has loaded.

#### DEV-08 — Push in the foreground
- [ ] Pass
- Account: Amir with the app open on Notifications
- Pre: none
- Steps: 1. Nadia declines one of his claims.
- Expect: a banner appears and the list refreshes with the new item without leaving the screen.

#### DEV-09 — Push stops after log out, and follows the account
- [ ] Pass
- Account: Amir on phone A
- Pre: receiving pushes
- Steps: 1. Amir logs out. 2. Nadia decides another of his requests. 3. Arjun signs in on phone A; Nadia decides one of Amir's and one of Arjun's requests.
- Expect: no push arrives on phone A while signed out; after Arjun signs in, phone A receives **only** Arjun's notifications.

#### DEV-10 — Notification permission denied
- [ ] Pass
- Account: any staff, fresh install
- Pre: deny the notification prompt
- Steps: 1. Use the app normally; have a request decided.
- Expect: no crash and no repeated prompts; the in-app Notifications feed still updates.

#### DEV-11 — Payslip PDF through the share sheet
- [ ] Pass
- Account: Amir on a phone
- Pre: payslip exists
- Steps: 1. **Download payslip (PDF)**. 2. Share to Mail / WhatsApp / Files. 3. Open the file on another device.
- Expect: a one-page, correctly laid-out PDF with Malaysian formatting (RM, 2 dp); file name / title identifies the period; fonts render (no boxes).

#### DEV-12 — Payroll export through the share sheet
- [ ] Pass
- Account: Sarah on a phone
- Pre: run exists
- Steps: 1. Export each of the four files and share to Mail and to Files. 2. Open each on a laptop (Excel / Numbers / a text editor).
- Expect: files arrive with the right names and extensions (`.csv` ×3, `cp39-<period>.txt`); CSVs open in columns with no garbled characters in names; amounts match the app.

#### DEV-13 — Interruptions
- [ ] Pass
- Account: staff on a phone
- Pre: none
- Steps: 1. Receive a phone call / lock the screen while on Clock In with the camera live, then return. 2. Background the app for 10+ minutes while "Currently working", then return.
- Expect: the camera preview resumes (or falls back gracefully); the Home timer shows the true elapsed time immediately on return.

#### DEV-14 — OTA update sanity (EAS Update)
- [ ] Pass
- Account: any
- Pre: an update published to the build's channel
- Steps: 1. Launch, close, relaunch.
- Expect: the new JS loads on the second launch; the session is preserved; no native-module crash (camera, location, file export all still work).

#### DEV-15 — Receipt capture with the real back camera
- [ ] Pass
- Account: Amir on a physical phone (the iOS Simulator has no camera) — run on one iPhone and one Android phone
- Pre: preview build; a real paper receipt (thermal print if possible); normal indoor light
- Steps: 1. New claim → **Add receipt photo** → take the photo holding the phone upright. 2. Submit. 3. Repeat holding the phone sideways (landscape). 4. Open both from the Claims list, and as Nadia from the Approvals card on a second device.
- Expect: the **back** camera is used; both photos appear upright (not rotated or mirrored) in the form thumbnail, the list thumbnail and the full-screen viewer on both devices; the merchant, date and total on the receipt are readable at full size. The capture screen respects the notch / home indicator (X and shutter fully tappable).

#### DEV-16 — Receipt upload size and speed
- [ ] Pass
- Account: staff on mobile data (not Wi-Fi), ideally the highest-resolution phone available (the iOS Simulator has no camera)
- Pre: weak signal if possible
- Steps: 1. Photograph a long, detailed receipt and submit the claim.
- Expect: the claim is submitted within ~10 s; it never fails with "Photo too large (max 2 MB). Please retake."; if the photo genuinely cannot be made small enough the form shows the "too large" note (STF-CLM-19) and the claim can still go in without it. No duplicate claim after a slow submit.

#### DEV-17 — Receipt camera: permission denied, then granted in Settings
- [ ] Pass
- Account: Amir on a physical phone (the iOS Simulator has no camera)
- Pre: camera permission denied for Teamora
- Steps: 1. New claim → **Add receipt photo** (expect the note, STF-CLM-18). 2. Open system Settings, allow Camera for Teamora, return to the app. 3. Open New claim again → **Add receipt photo**.
- Expect: after enabling, the camera opens and a receipt can be attached. On Android (where the app is not restarted by the permission change) note whether the form still shows the "Camera access is off…" note until the screen is re-opened — log it if so. The clock-in selfie uses the same permission: check Clock In still shows its live preview (DEV-02).

---

## H. Release smoke checklist (≈ 15 minutes)

Run on the exact build / update being released, against the target backend.
Two devices make it faster (one staff, one admin). Any failure blocks the release.

- [ ] **1.** Fresh launch shows Onboarding with the **Teamora** wordmark; Login shows **no demo credentials** on a production build *(AUTH-01, AUTH-02, AUTH-14)*
- [ ] **2.** Wrong password shows an error; `amir@lumi.com` signs in to staff Home *(AUTH-10, AUTH-05)*
- [ ] **3.** Home hero shows "Not clocked in yet" + **Clock In** (or the correct state for today) *(STF-HOME-02)*
- [ ] **4.** **Clock In** → **Verify & Clock In** succeeds; hero flips to "Currently working" with a timer starting near 0:00:00 and ticking *(STF-CLK-01, STF-HOME-03)*
- [ ] **5.** Attendance tab shows today's row with the clock-in time *(STF-ATT-03)*
- [ ] **6.** Leave: tiles render without broken words; apply a 1-day Annual Leave → appears as Pending *(STF-LVE-02, STF-LVE-05)*
- [ ] **7.** Claims: submit RM 10.00 → appears as Pending, pending total updates *(STF-CLM-02)*
- [ ] **8.** Payroll tab: latest payslip arithmetic adds up; **Download payslip (PDF)** opens the share sheet *(STF-PAY-01, STF-PAY-05)*
- [ ] **9.** Profile: every visible menu row opens something; **Log out** returns to Onboarding *(STF-PRO-02, AUTH-23)*
- [ ] **10.** `nadia@lumi.com` → staff Home with **Approvals** card; Amir's leave is in the inbox; **Approve** removes it *(AUTH-08, MGR-03)*
- [ ] **11.** Back as Amir: request shows Approved and the balance dropped by 1 *(X-DATA-02)*
- [ ] **12.** Clock out from Home → confirm → hero shows "Done for today" with a static total *(STF-HOME-04, STF-HOME-05)*
- [ ] **13.** `sarah@lumi.com` → admin Dashboard; KPIs load with real numbers *(AUTH-06, ADM-DASH-02)*
- [ ] **14.** Live attendance lists Amir as clocked in / out with the right time *(ADM-LIVE-01)*
- [ ] **15.** Staff → Amir → hub tiles load; open **Profile**, save a job-title change; open **Employment** → manager is still Nadia *(ADM-EMP-06, ADM-EMP-11)*
- [ ] **16.** Approve tab shows the pending claim; **Approve** it *(ADM-APR-02)*
- [ ] **17.** Payroll tab: **Run payroll** (or **Re-run**) produces a Draft whose Net = Gross − Statutory − PCB; Amir's line includes the approved claim *(ADM-PAY-02, ADM-PAY-04)*
- [ ] **18.** **Reports & export** table loads; **Export** on Contribution summary opens the share sheet *(ADM-EXP-01, ADM-EXP-03)*
- [ ] **19.** Profile → Company settings, Work locations, Company calendar each open and load *(ADM-PRO-02)*
- [ ] **20.** `admin@nusantara.com` → Staff list shows only Nusantara people *(X-TEN-01)*
- [ ] **21.** Physical device only: clock-in selfie appears on the Live board; a push arrives for a decided request *(DEV-01, DEV-07)*
- [ ] **22.** Login → **Forgot password?** shows the "Ask your HR admin to reset your password…" alert *(AUTH-15)*
- [ ] **23.** Staff Profile → **Change password**: a wrong current password shows "Current password is incorrect"; a correct change shows "Password changed", the device stays signed in, and the new password works after Log out *(AUTH-28, AUTH-27)*
- [ ] **24.** `sarah@lumi.com` → Staff → that employee → Employment → **Reset password** → set it back to `password` → the employee signs in with it *(ADM-EMP-36)*
- [ ] **25.** Staff Profile → **My details** shows the real record; save a phone number → "Phone number saved." and it is still there on re-open *(STF-PRO-05, STF-PRO-08)*
- [ ] **26.** Claim submitted **without** a receipt shows the grey receipt tile, and its approval card reads "No receipt attached" *(STF-CLM-16, MGR-17)*
- [ ] **27.** Physical device only (the iOS Simulator has no camera): New claim → **Add receipt photo** → take it → submit → the thumbnail shows in Claims, and the approver opens it full size from the claim card *(STF-CLM-15, MGR-16)*

Do **not** approve or mark payroll as paid during a smoke pass on a shared
environment — those steps are irreversible for the period (ADM-PAY-05, ADM-PAY-08).

---

## Known limitations / not yet built

Drawn from `/worklog` and from reading the code. These are **expected** gaps —
do not log them as new bugs, but do confirm each is still acceptable for the
release and that the UI never pretends otherwise.

**Payroll & statutory**
- Exact **EPF (KWSP) Form A / i-Akaun**, **PERKESO ASSIST** and **bank IBG** portal file formats are not produced — exports are generic CSVs. CP39 is a best-effort fixed-width text (`CP39-TXT-v1`), not the official e-CP39 CSV/XML; the screen says to review before submission.
- **PCB is an estimate**: no year-to-date accumulation or prior-PCB carry, no additional reliefs (lifestyle, medical…), no zakat rebate. Labelled "PCB (est.)".
- **Bonus / allowances** have no input — bonus is always 0; the MTD additional-remuneration formula is not modelled.
- **Employer cost** (employer EPF / SOCSO / EIS) appears only in the export table, not on the run screen.
- SOCSO / EIS use the wage-band midpoint at gazetted rates and may differ by a few sen from PERKESO's printed table; only Category 1 (under 60, citizen / PR) is modelled; Malaysia only.
- Pay date is always the last day of the month.
- The admin Payroll screen works on the **current calendar month only** — no period picker, no back-dated run from the app.
- Once a run is approved there is **no in-app Re-run**, so an employee given a salary afterwards cannot be added to that period.
- `payslips.paid_days` is a whole number (rounded) while money uses the exact fraction.
- Staff see only their **latest** payslip — no payslip history or per-period download; no "email me my payslip".
- Absence without a leave record does not deduct pay (Monthly basis).

**Leave**
- **Over-entitlement is not blocked** — a request beyond the balance is accepted and can make the balance negative.
- No overlap check between leave requests; no cancel / withdraw / edit of a request after submitting; a decision cannot be reversed.
- **Carry-forward expiry windows**, anniversary-based leave years, tenure tiers, encashment and pro-rating on termination are not built.
- Carry-forward only applies when a balance row for the previous leave year exists (created when balances were read that year).
- Mixed partial days inside a multi-day range are not supported (half-day / hourly leave is single-date).
- Approving leave does **not** write an ON_LEAVE attendance row, so "on leave today" on Home / Live board / Dashboard reflects attendance records, not approved leave.
- Leave-type default entitlement is whole days only; the app cannot apply leave for past dates.

**Attendance & geofence**
- **Mock-location (GPS spoofing) detection** is not built.
- **Clock-out is not geofenced** and takes no selfie.
- The selfie is proof of presence only — **no face matching / biometric verification**.
- One work location per employee; one clock-in and one clock-out per day (no breaks, no multiple sessions).
- "Late" is a fixed 09:05 Malaysia-time cut-off and the shift shown on Home is a fixed "9:00 AM – 6:00 PM" — neither follows the assigned shift.
- The Attendance "OT" figure is time worked beyond 9 h a day and is unrelated to approved overtime requests.
- Attendance history has no month picker; the Live board shows today only.

**Scheduling & calendar**
- Scheduling shows the current week only (no week navigation) and assigns one employee at a time; a shift cannot be deleted, only replaced.
- Birthdays are derived from employee records and cannot be created as calendar events.

**Claims**
- A claim takes **one** receipt photo, from the camera only (no gallery pick, no PDF, no multiple pages), and only at submit time — a receipt cannot be added, replaced or removed afterwards. Photos are capped at 2 MB.
- Receipt capture needs a physical device (no camera in the iOS Simulator).
- No claim categories or limits configuration; no claim edit / cancel.

**Notifications**
- Remote push needs a development / EAS build on a physical device (not Expo Go, not simulators).
- No per-notification read state or deep link to the related request — tapping a push opens the Notifications list.

**Account & app**
- **No email / self-service password reset** — "Forgot password?" only tells the user to ask their HR admin, who sets a temporary password by hand (ADM-EMP-36). The employee is not forced to change the temporary password at first sign-in.
- **An owner who forgets their password cannot be recovered in the app** — an HR admin cannot reset the owner, and the owner's own record has no Reset password button.
- After a password change or reset, other devices stay usable until their access token expires (up to 30 minutes) — the sign-out is not instant.
- Employee self-service editing is **phone number only** (My details); everything else is changed by HR. The saved phone number is not shown on any admin screen. The admin app has no "My details" screen.
- No employee deactivation / off-boarding screen; work email cannot be changed.
- The only password rule is length (8–72 characters, enforced on Change password and Reset password) — no complexity rule, no reuse history, no lockout after repeated wrong attempts. Check whether Register and Add employee enforce the same minimum in the build under test and log it if they do not.
- Documents & contracts, App settings and Privacy & security have no backing feature.
- English only (no i18n / language switcher); light mode only.
- Admins (OWNER / HR_ADMIN) have no self-service screens in the admin app (own clock-in, leave, claims, payslip, notifications).

---

## I. Round 2 changes (added 2 Oct 2026)

Cases for behaviour that changed after the first QA pass. Same format and accounts as above.

#### R2-01 — Staff never see an unapproved payslip
- **Steps:** As HR admin run payroll for the current month (leave it as Draft). Sign in as that month's employee → Payroll tab.
- **Expected:** The draft month is not listed. With no approved payslips the screen shows "No payslips yet". After the admin approves, it appears with "Approved · pay date …"; after Mark as paid it reads "Paid …".
- [ ] Pass

#### R2-02 — Payslip bank line is real or absent
- **Steps:** Admin sets Bank name + account number on an employee (Statutory & bank), approves a run. Open that employee's payslip and its PDF. Repeat for an employee with no bank details.
- **Expected:** "Bank ••last4" for the first; no bank text at all (and never "null" or "Maybank ••4821") for the second.
- [ ] Pass

#### R2-03 — Switching accounts shows no stale data
- **Steps:** Sign in as amir@lumi.com, open Home, Leave and Claims. Log out. Sign in as nadia@lumi.com.
- **Expected:** No flash of Amir's numbers, name or requests anywhere.
- [ ] Pass

#### R2-04 — Launching offline keeps you signed in
- **Steps:** Sign in, force-quit, turn on Airplane Mode, reopen.
- **Expected:** "Can't reach Teamora — You're still signed in" with Try again. Turn Airplane Mode off → Try again → lands in the app without logging in again.
- [ ] Pass

#### R2-05 — Approve / Decline failures are shown
- **Steps:** Open a pending request on two devices as two approvers. Approve on one, then tap Approve on the other.
- **Expected:** The second device shows an alert with the server's reason; the card does not silently disappear or stay stuck.
- [ ] Pass

#### R2-06 — Payroll action failures are shown
- **Steps:** As a MANAGER-level token (or with the server stopped) trigger Run / Approve / Mark as paid.
- **Expected:** An alert explains the failure; the status chip does not change.
- [ ] Pass

#### R2-07 — Leave beyond the balance is blocked (tracked types)
- **Steps:** As staff, apply for more Annual Leave days than "available". Then apply for Unpaid Leave of the same length.
- **Expected:** Annual: the form shows the available balance, Submit is blocked or the server replies "Not enough Annual Leave balance — you have N day(s) available." Unpaid: accepted.
- [ ] Pass

#### R2-08 — Overlapping leave is blocked
- **Steps:** With a pending or approved full-day request on a date, apply again covering that date. Then try AM half-day + PM half-day on one new date.
- **Expected:** First: "You already have a leave request covering these dates." Second: both half-days are accepted.
- [ ] Pass

#### R2-09 — Work start time and late grace drive "Late"
- **Steps:** Admin → Company settings → set Work start time 08:30, Late after 10 → Save defaults. Clock in as staff at 08:39, another at 08:41.
- **Expected:** 08:39 is Present, 08:41 is Late. Staff Home reads "Work starts 8:30 AM".
- [ ] Pass

#### R2-10 — Live Attendance is real
- **Steps:** Admin → Live Attendance. Tap each filter chip.
- **Expected:** No map, no pins, no site name unless the person clocked in at an assigned site. Counts match the filtered list; each empty filter shows its own empty state.
- [ ] Pass

#### R2-11 — Month and payslip steppers
- **Steps:** Staff Attendance → previous month and back. Staff Payroll → step between payslips.
- **Expected:** Data changes with the month; "next" is disabled at the current month / newest payslip.
- [ ] Pass

#### R2-12 — Admin notifications
- **Steps:** As staff submit a claim. As HR admin open the dashboard.
- **Expected:** Bell shows an unread dot; tapping opens Notifications with the new item; Mark all read clears the dot.
- [ ] Pass

#### R2-13 — Validation
- **Steps:** Try: blank company name; all working days off (company settings and an employee's Compensation); a 7-character password on Register, Add employee, Change password, Reset password; a duplicate Staff ID.
- **Expected:** Each is rejected with a readable message next to the field; nothing is saved.
- [ ] Pass

#### R2-14 — Admin edits Staff ID and phone
- **Steps:** Admin → employee → Profile → change Staff ID and Phone → Save. Clear the phone → Save.
- **Expected:** Values persist; the employee sees them in My details; a cleared phone shows "Not set".
- [ ] Pass

#### R2-15 — Join-date change re-prorates leave
- **Steps:** Add an employee with today's join date (prorated Annual Leave). Change the join date to 1 January.
- **Expected:** Entitlement rises to the full-year figure. If the admin had overridden the entitlement by hand, it is left untouched.
- [ ] Pass

#### R2-16 — Reset password asks first and cannot target yourself
- **Steps:** Admin → employee → Employment → Reset password → enter a password → Reset.
- **Expected:** A confirmation ("Reset …'s password?") appears before anything changes. Your own record shows no Reset button.
- [ ] Pass

---

## J. Round 3 changes (added 3 Oct 2026)

#### R3-01 — Forgot password with a one-time code (dev / preview builds)
- **Needs:** backend started with `PASSWORD_RESET_EXPOSE_CODE=true`.
- **Steps:** Login → Forgot password? → enter a real account's email → Send code. Enter the code from the "Dev only" note and a new 8+ character password twice → Set new password. Sign in with the new password; try the old one.
- **Expected:** New password works, old one fails, other devices are signed out. Restore the demo password afterwards.
- [ ] Pass

#### R3-02 — Unknown email reveals nothing
- **Steps:** Request a code for an email with no account.
- **Expected:** Same "If that email has a Teamora account…" screen, no dev code, no error.
- [ ] Pass

#### R3-03 — Wrong, expired and reused codes
- **Steps:** Enter a wrong code; reuse a code that already worked; wait 10 minutes and use an old code; enter 5 wrong codes then the right one.
- **Expected:** Each shows "That code is incorrect or has expired." After 5 wrong tries the code is dead and a new one is needed.
- [ ] Pass

#### R3-04 — Resend limit
- **Steps:** Tap Resend code four times within 15 minutes.
- **Expected:** No error is shown, but only the first three produce a code; the third stays valid.
- [ ] Pass

#### R3-05 — Production build does not offer the code flow
- **Steps:** On a production build tap Forgot password?
- **Expected:** The "Ask your HR admin" alert, not the code screen (until an email provider is connected).
- [ ] Pass

#### R3-06 — Clock in again after clocking out
- **Steps:** Clock in, clock out, wait a few minutes, tap "Clock in again" on Home and complete clock-in, then clock out.
- **Expected:** Timer continues from the earlier worked time (the gap is not counted), Home shows "Break N min", the final worked total excludes the break, the first clock-in time and Late/Present status are unchanged. With an assigned site, clocking in again outside the radius is refused.
- [ ] Pass

#### R3-07 — Clear pay settings back to the company default
- **Steps:** Admin → employee → Compensation. Empty the hours field → Save. Choose "Use company default" for working days and pay basis → Save. Empty the salary → Save.
- **Expected:** Hours, days and basis show the company defaults again and follow later changes to company settings; with no salary the employee is left out of the next payroll run.
- [ ] Pass

#### R3-08 — Calendar dates sit under the right weekday
- **Steps:** Staff → Calendar. Compare today's date and the 1st of the month with the phone's own Calendar app. Step to next and previous month.
- **Expected:** Seven columns in every row; each date is under its true weekday.
- [ ] Pass

#### R3-09 — Compensation shows which values are personal
- **Steps:** Admin → an employee with no personal pay settings → Compensation. Then set Daily + a custom week + 6 hours, save, reopen.
- **Expected:** First: "Company default (…)" selected, "Use company default" ticked, hours empty with the default as placeholder. After saving: the personal values show and the default options are unselected. Saving an untouched form changes nothing.
- [ ] Pass

#### R3-10 — Payroll for a past month
- **Steps:** Admin → Payroll → step back a month and forward again.
- **Expected:** Totals, status and breakdown are for the month shown; Run / Approve / Mark as paid act on that month; "next" is disabled at the current month.
- [ ] Pass

#### R3-11 — Data refreshes without restarting the app
- **Steps:** On device A (admin) open Payroll and Approvals. On device B approve the payroll run and decide a request. On A, switch tabs, or background the app for 30+ seconds and return.
- **Expected:** A shows the new status and counts without a restart.
- [ ] Pass

#### R3-12 — Decline asks first
- **Steps:** Tap Decline on a leave, a claim and an overtime request; choose Cancel, then Decline.
- **Expected:** "Decline this …?" appears each time; Cancel changes nothing; Decline removes the card and notifies the employee. Approve is a single tap. Decline and Approve are both full, readable buttons.
- [ ] Pass

#### R3-13 — "Today" is Malaysian time before 8 a.m.
- **Steps:** Between midnight and 8 a.m., open the staff Calendar, the admin Schedule, and Payroll → Reports & export.
- **Expected:** Today's date is highlighted correctly, the schedule opens on today, and the export timestamp shows local time.
- [ ] Pass

#### R3-14 — Statutory export rows
- **Steps:** Admin → Payroll (a month with a run) → Reports & export → scroll to Export files.
- **Expected:** Four rows, each with a name and description (Contribution summary, Bank payment, Full payroll, CP39) and a compact Export button; each opens the share sheet with a file. Header reads the month in words.
- [ ] Pass

#### R4-01 — Import Malaysian public holidays
- **Steps:** Admin → Profile → Company calendar → "Add Malaysian public holidays". Check the year chips; untick/tick a few rows; Add.
- **Expected:** Rows that differ by state start unticked and show their note; "Add N holidays" counts the ticks; after adding, those rows show "Added" and can't be ticked; the holidays appear on the admin and staff calendars; importing again adds nothing twice.
- [ ] Pass

#### R4-02 — Decline with a reason
- **Steps:** As an approver tap Decline on a leave, a claim and an overtime request. Type a reason on one, leave another blank. Tap low inside the reason box.
- **Expected:** A sheet asks "Decline this …?"; the whole box is tappable; Cancel changes nothing. The employee's notification reads "… was declined: {reason}" (or just "was declined." with no reason), and their Leave / Claims / Overtime list shows "Reason: …" under the declined item.
- [ ] Pass

#### R4-03 — Decline sheet keyboard (Android + iPhone)
- **Steps:** Open the decline sheet and type a long reason.
- **Expected:** The keyboard never covers the field or the Decline button; a counter appears in the last 50 characters; 300 is the limit.
- [ ] Pass

---

## K. Simulator pass 3 (added 3 Oct 2026)

#### R5-01 — Company profile edits are saved
- **Steps:** Admin → Company settings → change Phone (and any other profile field) → Save changes. Leave the screen, reopen Company settings.
- **Expected:** The new values are still there after reopening (and after an app restart).
- [ ] Pass

#### R5-02 — Add employee with a start date
- **Steps:** Admin → Staff → Add employee. Fill name, email, an 8+ character temporary password; leave Start date as today; Add. Open Leave entitlement.
- **Expected:** The temporary password is readable as you type (not masked) and iOS does not offer to save it. Entitlements are prorated from today (e.g. Annual 16 → about 4 in October). The Profile tile shows staff ID / phone when there is no job title.
- [ ] Pass

#### R5-03 — Leave entitlement override
- **Steps:** Employee → Leave entitlement. Retype a value as-is (e.g. "3.50" for 3.5) → Save is still disabled. Change one value → Save. Change the employee's join date afterwards.
- **Expected:** Only real changes enable Save; Unpaid Leave isn't listed; the value you set survives the join-date change while the other types re-prorate.
- [ ] Pass

#### R5-04 — Payroll actions sit above the breakdown
- **Steps:** Admin → Payroll for a month with a run (Draft, then Approved).
- **Expected:** Approve / Mark as paid / Re-run and "Reports & export" appear right under the totals, before the per-employee list — no scrolling past every payslip.
- [ ] Pass

#### R5-05 — Staff figures are labelled
- **Steps:** Staff Home → Today's summary; Staff Profile → stat tiles.
- **Expected:** Claims pending shows "RM 0.00" (with currency). Profile shows "Annual left" equal to the Annual Leave figure on Home — not a sum of every leave type.
- [ ] Pass

---

## L. Account deletion, deactivation, privacy (added 3 Oct 2026)

#### R6-01 — Employee asks for account deletion
- **Steps:** Staff → Profile → "Delete my account" → optional reason → Send request.
- **Expected:** "Request sent" with today's date; HR admins and the owner get an "Account deletion request" notification (with the reason if given). Sending again within 24h sends nothing new. The owner does not see this row (they get "Delete company and account").
- [ ] Pass

#### R6-02 — Deactivate and reactivate an employee
- **Steps:** Admin → employee → Employment → Deactivate account → confirm. Try to use the app on that employee's phone; try to sign in. Then Reactivate.
- **Expected:** Their open session stops working at once and sign-in fails; they show "Inactive" in the staff list (greyed, at the bottom) and are left out of payroll runs and shift assignment; their history stays. Deactivating a manager warns that their reports go to the owner. After Reactivate they can sign in again. You cannot deactivate yourself or the owner.
- [ ] Pass

#### R6-03 — Owner deletes the company (use a throwaway company)
- **Steps:** Register a new company → Profile → "Delete company and account" (or Company settings → Danger zone). Read the list; tap "Go to Payroll"; come back; type the company name and password → Delete everything → confirm.
- **Expected:** Button stays disabled until the name matches; wrong password shows an error; on success "Company deleted" and you land on onboarding; signing in with that owner fails; other companies are untouched.
- [ ] Pass

#### R6-04 — Privacy notice and terms
- **Steps:** Login and Register footers → Terms / Privacy; Profile (staff and admin) → Privacy & terms.
- **Expected:** Both documents open signed in or out, show "Draft — pending legal review", and describe only what the app actually collects (selfie optional, location only at clock-in with an assigned site).
- [ ] Pass
