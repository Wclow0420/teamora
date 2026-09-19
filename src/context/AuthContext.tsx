import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '@/api/endpoints';
import { setUnauthorizedHandler } from '@/api/client';
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

type Status = 'loading' | 'authenticated' | 'unauthenticated';

type AuthState = {
  status: Status;
  employee: EmployeeResponse | null;
  role: Role | null;
  signIn: (email: string, password: string) => Promise<EmployeeResponse>;
  signUp: (companyName: string, fullName: string, email: string, password: string) => Promise<EmployeeResponse>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null);

  const signOut = useCallback(async () => {
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
    setEmployee(null);
    setStatus('unauthenticated');
  }, []);

  // Restore a persisted session on launch.
  useEffect(() => {
    let active = true;
    (async () => {
      const tokens = await loadTokens();
      if (!tokens) {
        if (active) setStatus('unauthenticated');
        return;
      }
      try {
        const me = await authApi.me();
        if (!active) return;
        setEmployee(me);
        setStatus('authenticated');
        void registerPushToken();
      } catch {
        await clearTokens();
        if (active) setStatus('unauthenticated');
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  // A failed refresh (inside the API client) forces a sign-out.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void signOut();
    });
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email.trim(), password);
    await saveTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setEmployee(res.employee);
    setStatus('authenticated');
    void registerPushToken();
    return res.employee;
  }, []);

  const signUp = useCallback(async (companyName: string, fullName: string, email: string, password: string) => {
    const res = await authApi.register({ companyName, fullName, email: email.trim(), password });
    await saveTokens({ accessToken: res.accessToken, refreshToken: res.refreshToken });
    setEmployee(res.employee);
    setStatus('authenticated');
    void registerPushToken();
    return res.employee;
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      status,
      employee,
      role: employee?.role ?? null,
      signIn,
      signUp,
      signOut,
    }),
    [status, employee, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>');
  return ctx;
}
