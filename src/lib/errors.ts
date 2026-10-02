import { Alert } from 'react-native';
import { ApiError } from '@/api/client';

/** What we say when the request never reached the server (no signal, server down). */
export const OFFLINE_MESSAGE = "Can't reach Teamora. Check your connection and try again.";

/**
 * The human message for a failed request: the server's own message when it gave
 * one (`ApiError`), otherwise a plain connection hint — never a raw
 * "Network request failed".
 */
export function errorMessage(e: unknown, fallback: string = OFFLINE_MESSAGE): string {
  return e instanceof ApiError && e.message ? e.message : fallback;
}

/** Show a failed action in an alert, so nothing fails silently. */
export function alertError(title: string, e: unknown, fallback?: string): void {
  Alert.alert(title, errorMessage(e, fallback));
}
