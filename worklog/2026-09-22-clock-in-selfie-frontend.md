# 2026-09-22 — Clock-in selfie camera flow (frontend)

Frontend for the clock-in selfie attendance proof (spec v1). Backend (V13 photo
column + `GET /api/attendance/records/{id}/photo` + `LiveStaffRow` fields) is
tracked separately; this entry is the app side only. Ships in the SAME batched
native preview build as the geofence work — `expo-camera` is a new native dep, so
no OTA.

## What changed
- **Native dep:** `expo-camera` (~17.0.10) via `npx expo install`.
- **`app.config.ts`:** added the `expo-camera` config plugin (cameraPermission
  string, `recordAudioAndroid: false`) alongside the existing `expo-location`
  plugin; iOS `NSCameraUsageDescription` ("Teamora uses the camera to take your
  clock-in photo.") and Android `CAMERA` permission. Location entries, bundle
  ids, updates, and runtimeVersion untouched.
- **`src/lib/selfie.ts`** (new): `captureSelfieBase64(camera)` → compressed
  front-camera JPEG as bare base64 (`quality: 0.35`, `base64: true`). Deliberately
  no `skipProcessing` (it discards `quality` → bloats the payload). Returns null
  on any failure so callers fall back to a photo-less clock-in.
- **`app/(staff)/clock-in.tsx`:** replaced the fake face circle + "Scanning your
  face…" chip with a live front-camera `CameraView`, gated on camera permission
  (requested on focus via `useFocusEffect` + `useCameraPermissions`). "Verify &
  Clock In" now: capture selfie (best-effort) → existing geofence/location flow →
  `clockIn({ latitude, longitude, photoBase64 })`. Permission denied/unavailable
  → subtle inline note + clock in WITHOUT a photo (non-blocking). Assigned/
  unassigned logic (`me.data.workLocationId`) and geofence error handling kept.
  Dark backdrop + bottom location card + CTA design preserved.
- **`src/api/types.ts`:** `ClockInBody` gains optional `photoBase64`;
  `LiveStaffRow` gains `attendanceRecordId?: string|null` + `hasPhoto?: boolean`.
- **`src/api/endpoints.ts`:** `attendanceApi.photoUrl(recordId)` builds the
  absolute auth-guarded photo URL from `API_BASE_URL`. `clockIn` already forwards
  the full body (now incl. `photoBase64`).
- **`src/components/attendance/SelfieThumb.tsx`** (new): admin thumbnail. RN
  `<Image>` with an `Authorization: Bearer` header (token from `tokenStore`),
  falls back to the tinted initial `Avatar` when there's no photo / no token /
  the image errors.
- **`app/admin/live.tsx`:** rows with `hasPhoto` show the selfie thumbnail; tap
  opens a full-screen `Modal` viewer (same Bearer-header image, tap to close).
  Avatar fallback otherwise.
- **`app/(staff)/(tabs)/profile.tsx`:** no-fake-data cleanup — Payslips meta is
  now the real `usePayslips().data?.length` ("N available", blank until loaded);
  Documents meta removed (no backing data yet). Was hardcoded "12 available" /
  "4 files".

## Verified
- `npm run typecheck` clean (strict, no `any`).
- Camera APIs no-op in Expo Go / simulators — expected; the live preview + capture
  need the dev/preview build. Not yet exercised end-to-end on device (needs the
  batched native build + backend V13).

## Notes for next person
- Needs the NEW native build (expo-camera) + backend V13 before the selfie flow
  works end to end. The clock-in itself already degrades gracefully without a
  photo, so the app is safe to ship even if a device denies the camera.
