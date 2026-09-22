import type { CameraView } from 'expo-camera';

/**
 * Capture a compressed front-camera JPEG from a mounted {@link CameraView} and
 * return it as a bare base64 string (no `data:` prefix) for the clock-in body.
 *
 * Kept deliberately small — `quality: 0.35` yields a ~100–300 KB selfie, which
 * travels fine as base64 in the JSON clock-in request (no multipart). The photo
 * is proof-of-presence, not biometric verification, so light compression is fine.
 *
 * Returns `null` when no camera is available or the capture yields no data, so
 * callers can fall back to a photo-less clock-in rather than blocking staff.
 */
export async function captureSelfieBase64(
  camera: CameraView | null,
): Promise<string | null> {
  if (!camera) return null;
  try {
    const photo = await camera.takePictureAsync({
      // Heavy compression keeps the base64 payload small (~100–300 KB). Note we
      // deliberately DON'T set `skipProcessing` — that flag discards `quality`
      // (and orientation fixups), which would balloon the upload.
      quality: 0.35,
      base64: true,
      exif: false,
    });
    return photo?.base64 ?? null;
  } catch {
    // Never let a camera hiccup block the clock-in — fall back to no photo.
    return null;
  }
}
