import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import type { ExportFile } from '@/api/types';

/**
 * Writes a text export (CSV / plain text) to the app cache and opens the native
 * share sheet so the admin can email it to their accountant, save it to Files, or
 * upload it to a portal. Uses `expo-file-system` (write) + `expo-sharing`.
 *
 * Both work in Expo Go and dev/EAS builds. Failures are surfaced as thrown Errors
 * with a friendly message — callers should catch and show them, never crash.
 */
export async function shareExport({
  filename,
  mimeType,
  content,
  dialogTitle,
}: ExportFile & { dialogTitle?: string }): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) {
    throw new Error('Sharing isn’t available on this device.');
  }

  let uri: string;
  try {
    const file = new File(Paths.cache, filename);
    // Overwrite any stale export with the same name from a previous share.
    file.create({ overwrite: true });
    file.write(content);
    uri = file.uri;
  } catch {
    throw new Error('Couldn’t prepare the export file. Please try again.');
  }

  await Sharing.shareAsync(uri, { mimeType, dialogTitle: dialogTitle ?? filename });
}
