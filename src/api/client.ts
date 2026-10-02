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

/**
 * The sentence to show a person for an error body. Bean-validation failures
 * arrive as a generic "Validation failed" with the readable text in
 * `fieldErrors`, so the first field error wins over the generic message.
 */
function readableMessage(body: ApiErrorBody | undefined): string | undefined {
  if (!body) return undefined;
  const fieldError = Object.values(body.fieldErrors ?? {}).find((m) => typeof m === 'string' && m.trim().length > 0);
  const generic = !body.message || /^validation failed/i.test(body.message.trim());
  return generic ? fieldError ?? body.message : body.message;
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
/**
 * `rejected` = the server refused the refresh token (session really is over).
 * `unreachable` = no network / server error — the session may still be valid,
 * so the caller must NOT clear tokens.
 */
type RefreshResult = 'ok' | 'rejected' | 'unreachable';

let refreshing: Promise<RefreshResult> | null = null;

async function doRefresh(): Promise<RefreshResult> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return 'rejected';
  try {
    const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (res.status >= 500) return 'unreachable';
    if (!res.ok) return 'rejected';
    const data = (await res.json()) as AuthResponse;
    await saveTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    return 'ok';
  } catch {
    return 'unreachable';
  }
}

async function refreshOnce(): Promise<RefreshResult> {
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
    const result = await refreshOnce();
    if (result === 'ok') return send<T>(path, options, true);
    // Couldn't reach the server to refresh — keep the session, surface a retryable error.
    if (result === 'unreachable') throw new ApiError(503, "Can't reach Teamora. Check your connection and try again.");
    await clearTokens();
    onUnauthorized?.();
    throw new ApiError(401, 'Session expired');
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      // Non-JSON body (e.g. a proxy's HTML error page) — fall through to the status message.
      if (res.ok) throw new ApiError(res.status, 'Unexpected response from the server.');
    }
  }

  if (!res.ok) {
    const errBody = (data ?? undefined) as ApiErrorBody | undefined;
    throw new ApiError(res.status, readableMessage(errBody) ?? `Request failed (${res.status})`, errBody);
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
