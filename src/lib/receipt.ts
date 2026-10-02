import type { CameraView } from 'expo-camera';

/** Server cap is 2 MB of image bytes; stay a little under it. */
const MAX_BYTES = 1.9 * 1024 * 1024;

export type ReceiptCapture =
  | { ok: true; base64: string }
  /** `unavailable` — no camera / capture failed. `too-large` — over the upload cap even when recompressed. */
  | { ok: false; reason: 'unavailable' | 'too-large' };

/** Decoded byte size of a bare base64 string. */
function base64Bytes(b64: string): number {
  return Math.floor((b64.length * 3) / 4);
}

async function shoot(camera: CameraView, quality: number): Promise<string | null> {
  // No `skipProcessing` — that flag discards `quality` and balloons the upload
  // (same reasoning as the clock-in selfie in `selfie.ts`).
  const photo = await camera.takePictureAsync({ quality, base64: true, exif: false });
  return photo?.base64 ?? null;
}

/**
 * Capture a compressed back-camera JPEG of a receipt from a mounted
 * {@link CameraView}, as a bare base64 string (no `data:` prefix) for the
 * claim-submit body.
 *
 * `quality: 0.4` keeps a receipt legible while travelling fine as base64 in the
 * JSON request. Rear sensors are much larger than the selfie camera, so if the
 * first shot is still over the server's 2 MB cap we retake once, harder
 * compressed, before giving up — the caller never sends a photo the API would
 * reject.
 */
export async function captureReceiptBase64(camera: CameraView | null): Promise<ReceiptCapture> {
  if (!camera) return { ok: false, reason: 'unavailable' };
  try {
    let shot = await shoot(camera, 0.4);
    if (!shot) return { ok: false, reason: 'unavailable' };
    if (base64Bytes(shot) > MAX_BYTES) {
      shot = await shoot(camera, 0.15);
      if (!shot) return { ok: false, reason: 'unavailable' };
      if (base64Bytes(shot) > MAX_BYTES) return { ok: false, reason: 'too-large' };
    }
    return { ok: true, base64: shot };
  } catch {
    return { ok: false, reason: 'unavailable' };
  }
}

/**
 * Pick a moderate still size from the camera's supported list (`"1920x1080"`
 * style entries) — big enough to read a receipt, small enough to upload. Returns
 * `undefined` to keep the device default when nothing suitable is listed.
 */
export function pickReceiptPictureSize(sizes: string[]): string | undefined {
  let best: { label: string; longEdge: number } | undefined;
  for (const label of sizes) {
    const m = /^(\d+)x(\d+)$/.exec(label);
    if (!m) continue;
    const longEdge = Math.max(Number(m[1]), Number(m[2]));
    if (longEdge < 1000 || longEdge > 2000) continue;
    if (!best || longEdge > best.longEdge) best = { label, longEdge };
  }
  return best?.label;
}

/** A displayable URI for a just-captured (not yet uploaded) receipt. */
export function receiptDataUri(base64: string): string {
  return `data:image/jpeg;base64,${base64}`;
}
