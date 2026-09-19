import { API_BASE_URL } from './config';
import { ApiErrorBody, AuthResponse } from './types';
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from './tokenStore';

/** Error thrown for any non-2xx response, carrying the parsed backend body. */
export class ApiError extends Error {
  status: number;
  body?: ApiErrorBody;
  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

/** AuthContext registers a callback so a failed refresh forces a sign-out. */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

type Options = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Attach the Bearer access token (default true). */
  auth?: boolean;
};

// Single-flight refresh: concurrent 401s share one refresh round-trip.
let refreshing: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = (await res.json()) as AuthResponse;
    await saveTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    return true;
  } catch {
    return false;
  }
}

async function refreshOnce(): Promise<boolean> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function send<T>(path: string, options: Options, isRetry: boolean): Promise<T> {
  const { method = 'GET', body, auth = true } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getAccessToken();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  // Try a one-time refresh on 401, then retry the original request.
  if (res.status === 401 && auth && !isRetry) {
    const ok = await refreshOnce();
    if (ok) return send<T>(path, options, true);
    await clearTokens();
    onUnauthorized?.();
    throw new ApiError(401, 'Session expired');
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const errBody = (data ?? undefined) as ApiErrorBody | undefined;
    throw new ApiError(res.status, errBody?.message ?? `Request failed (${res.status})`, errBody);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, auth = true) => send<T>(path, { method: 'GET', auth }, false),
  post: <T>(path: string, body?: unknown, auth = true) => send<T>(path, { method: 'POST', body, auth }, false),
  put: <T>(path: string, body?: unknown, auth = true) => send<T>(path, { method: 'PUT', body, auth }, false),
  patch: <T>(path: string, body?: unknown, auth = true) => send<T>(path, { method: 'PATCH', body, auth }, false),
  del: <T>(path: string, auth = true) => send<T>(path, { method: 'DELETE', auth }, false),
};
