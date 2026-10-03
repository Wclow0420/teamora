import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { authApi } from '@/api/endpoints';
import { ApiError, setUnauthorizedHandler } from '@/api/client';
import { queryClient } from '@/api/queryClient';
import { clearTokens, getRefreshToken, loadTokens, saveTokens } from '@/api/tokenStore';
import { registerPushToken, unregisterPushToken } from '@/notifications/push';
import type { EmployeeResponse, Role } from '@/api/types';

/**
 * Real authentication backed by the Spring Boot API. Holds the signed-in
 * employee + role, persists JWTs in the device keychain, restores the session
 * on launch, and signs out if a token refresh fails.
 *
 * One login for everyone: the server returns the role and the router sends
 * management roles to the admin app and EMPLOYEE to the staff app. Access is
 * enforced server-side by RBAC (admin APIs return 403 for non-managers), so
 * there is no client-side "secret" gate.
 */

/**
 * `offline` = we hold tokens but couldn't reach the server to confirm them
 * (no signal / server down). The session is kept; the entry gate offers Retry.
 */
type Status = 'loading' | 'authenticated' | 'unauthenticated' | 'offline';

type AuthState = {
  status: Status;
  employee: EmployeeResponse | null;
  role: Role | null;
  signIn: (email: string, password: string) => Promise<EmployeeResponse>;
  /**
   * Create a company + its owner and sign in. Like `signIn`, this only flips
   * `status` — it never navigates. Nothing in the app redirects on a status
   * change (the role redirect in `app/index.tsx` only renders at "/", and the
   * admin layout's guard isn't mounted under `(auth)`), which is what lets the
   * setup wizard call this mid-flow and simply carry on to its next step.
   * `deferPush` skips the notification-permission prompt so it doesn't interrupt
   * the wizard; the caller registers for push when the flow ends.
   */
  signUp: (
    companyName: string,
    fullName: string,
    email: string,
    password: string,
    options?: { deferPush?: boolean },
  ) => Promise<EmployeeResponse>;
  signOut: () => Promise<void>;
  /** Re-attempt the session restore after an `offline` launch. */
  retryRestore: () => Promise<void>;
};

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);

  // Single-flight sign-out. When the account no longer exists server-side (the
  // company was just deleted, or HR deactivated it), the push-token removal
  // below 401s, the client's failed refresh calls the unauthorized handler —
  // which is signOut again. Without this guard that loops; with it the nested
  // call just joins the sign-out already in progress.
  const signingOut = useRef<Promise<void> | null>(null);

  const signOut = useCallback(() => {
    if (signingOut.current) return signingOut.current;
    const run = (async () => {
      await unregisterPushToken(); // while still authenticated
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        try {
          await authApi.logout(refreshToken);
        } catch {
          // ignore — we clear locally regardless
        }
      }
      await clearTokens();
      // Drop every cached response so the next account never sees this one's data.
      queryClient.clear();
      setEmployee(null);
      setStatus('unauthenticated');
    })().finally(() => {
      signingOut.current = null;
    });
    signingOut.current = run;
    return run;
  }, []);

  /**
   * Restore a persisted session. Only a definite rejection (401/403) ends the
   * session — a network failure or server error keeps the tokens and reports
   * `offline`, so a patchy connection never logs anyone out.
   */
  const restore = useCallback(async (isActive: () => boolean = () => true) => {
    const tokens = await loadTokens();
    if (!tokens) {
      if (isActive()) setStatus('unauthenticated');
      return;
    }
    try {
      const me = await authApi.me();
      if (!isActive()) return;
      setEmployee(me);
      setStatus('authenticated');
      void registerPushToken();
    } catch (e) {
      const rejected = e instanceof ApiError && (e.status === 401 || e.status === 403);
      if (rejected) {
        await clearTokens();
        queryClient.clear();
        if (isActive()) setStatus('unauthenticated');
      } else if (isActive()) {
        setStatus('offline');
      }
    }
  }, []);

  useEffect(() => {
    let active = true;
    void restore(() => active);
    return () => {
      active = false;
    };
  }, [restore]);

  const retryRestore = useCallback(async () => {
    setStatus('loading');
    await restore();
  }, [restore]);

  // A failed refresh (inside the API client) forces a sign-out.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void signOut();
    });
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email.trim(), password);
    queryClient.clear(); // never inherit a previous account's cache
    await saveTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setEmployee(res.employee);
    setStatus('authenticated');
    void registerPushToken();
    return res.employee;
  }, []);

  const signUp = useCallback(
    async (companyName: string, fullName: string, email: string, password: string, options?: { deferPush?: boolean }) => {
      const res = await authApi.register({ companyName, fullName, email: email.trim(), password });
      queryClient.clear(); // never inherit a previous account's cache
      await saveTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
      setEmployee(res.employee);
      setStatus('authenticated');
      if (!options?.deferPush) void registerPushToken();
      return res.employee;
    },
    [],
  );

  const value = useMemo<AuthState>(
    () => ({
      status,
      employee,
      role: employee?.role ?? null,
      signIn,
      signUp,
      signOut,
      retryRestore,
    }),
    [status, employee, signIn, signUp, signOut, retryRestore],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>');
  return ctx;
}
