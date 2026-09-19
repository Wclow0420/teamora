# 2026-06-21 — Assign-shift form, real in-app notifications & push

## 1. Assign-shift form
`app/admin/shift-assign.tsx` (employee + date + shift type → `useAssignShift`),
reached from the Schedule "+". Registered in the admin stack.

## 2. Event-driven in-app notifications
- `NotificationService.create(...)` now also fans out a push (see below).
  Expanded `NotificationType` (added CLAIM_REJECTED, OVERTIME_APPROVED/REJECTED,
  APPROVAL_REQUEST).
- New `ApprovalNotifier` (resolves approver = reporting manager → owner) wired
  into Leave/Claim/Overtime services:
  - **on submit** → notify the approver ("New leave/claim/overtime …").
  - **on approve/reject** → notify the requester ("Leave approved", etc.).
- Verified live: Amir applies → **Nadia (manager) notified**; Nadia approves →
  **Amir notified**.

## 3. Push notifications (Expo)
- Backend: `push_tokens` table (migration **V8**), `PushToken` entity/repo,
  `ExpoPushService` (`@Async`, posts to Expo's push API, best-effort), token
  register/unregister endpoints. `@EnableAsync` added.
- App: `expo-notifications` + `expo-device`; `src/notifications/push.ts`
  (permission → Expo token → register with API; **guards Expo Go / simulators**),
  `NotificationListeners` (foreground receipt refetches the feed; tap opens
  Notifications). Registration is wired into AuthContext (on sign-in / session
  restore) and unregister on sign-out.

> **Push delivery requires a development/EAS build** — Expo Go (SDK 53+) dropped
> remote push, so registration no-ops there. In-app notifications work
> everywhere; the push path is ready for a dev build (the EAS projectId is set).

## Verification
- Backend: `./scripts/test-backend.sh` → **34 tests, BUILD SUCCESS**
  (incl. `NotificationFlowIT`: approve → requester notified; push-token upsert).
  V8 migrates on a fresh DB. Live smoke confirmed the notify flow + push-token 204.
- App: `tsc` clean + iOS bundle builds.

## Follow-ups
- A real device/EAS build is needed to actually receive pushes (Android also
  needs FCM creds in EAS). The Schedule "+" doesn't pre-fill the selected day yet
  (`date` param plumbed, just not passed).
