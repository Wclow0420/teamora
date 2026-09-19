import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Resolves the backend base URL. Priority:
 *  1. EXPO_PUBLIC_API_URL env (set this for staging/prod).
 *  2. Derived from the Metro dev host — so a physical device on the same Wi-Fi
 *     hits your Mac's LAN IP automatically (e.g. http://192.168.1.5:8080).
 *  3. Platform fallback (iOS sim → localhost, Android emulator → 10.0.2.2).
 *
 * The Spring Boot API listens on :8080.
 */
const API_PORT = 8080;

function deriveFromDevHost(): string | null {
  // e.g. "192.168.1.5:8081" (Metro) → use the host with the API port.
  const legacy = Constants as unknown as {
    manifest?: { debuggerHost?: string };
    manifest2?: { extra?: { expoGo?: { debuggerHost?: string } } };
  };
  const hostUri =
    Constants.expoConfig?.hostUri ??
    legacy.manifest?.debuggerHost ??
    legacy.manifest2?.extra?.expoGo?.debuggerHost;
  if (!hostUri || typeof hostUri !== 'string') return null;
  const host = hostUri.split(':')[0];
  if (!host) return null;
  return `http://${host}:${API_PORT}`;
}

function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL;
  if (fromEnv) return fromEnv.replace(/\/$/, '');

  const derived = deriveFromDevHost();
  if (derived) return derived;

  if (Platform.OS === 'android') return `http://10.0.2.2:${API_PORT}`;
  return `http://localhost:${API_PORT}`;
}

export const API_BASE_URL = resolveBaseUrl();
