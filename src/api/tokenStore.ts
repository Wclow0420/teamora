import * as SecureStore from 'expo-secure-store';

/**
 * Persists the JWT access + refresh tokens in the device keychain/keystore.
 * A small in-memory cache avoids an async read on every request.
 */
const ACCESS_KEY = 'teamora.accessToken';
const REFRESH_KEY = 'teamora.refreshToken';

export type Tokens = { accessToken: string; refreshToken: string };

let cache: Tokens | null = null;

export async function loadTokens(): Promise<Tokens | null> {
  if (cache) return cache;
  const [accessToken, refreshToken] = await Promise.all([
    SecureStore.getItemAsync(ACCESS_KEY),
    SecureStore.getItemAsync(REFRESH_KEY),
  ]);
  if (accessToken && refreshToken) {
    cache = { accessToken, refreshToken };
    return cache;
  }
  return null;
}

export function getAccessToken(): string | null {
  return cache?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return cache?.refreshToken ?? null;
}

export async function saveTokens(tokens: Tokens): Promise<void> {
  cache = tokens;
  await Promise.all([
    SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken),
    SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken),
  ]);
}

export async function clearTokens(): Promise<void> {
  cache = null;
  await Promise.all([
    SecureStore.deleteItemAsync(ACCESS_KEY),
    SecureStore.deleteItemAsync(REFRESH_KEY),
  ]);
}
