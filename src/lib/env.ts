import Constants from 'expo-constants';

export type AppEnvironment = 'development' | 'preview' | 'production';

/** The environment baked in by `app.config.ts` (`extra.APP_ENV`). */
export function appEnvironment(): AppEnvironment {
  const raw: unknown = Constants.expoConfig?.extra?.APP_ENV;
  return raw === 'development' || raw === 'preview' ? raw : 'production';
}

/**
 * Whether internal-only helpers (e.g. the demo logins on the sign-in screen) may
 * be shown. Deliberately fail-safe: `app.config.ts` falls back to 'development'
 * when APP_ENV is unset, so a release bundle published without it must NOT count
 * as internal. A 'development' value is therefore only trusted alongside
 * `__DEV__` (a Metro dev bundle); 'preview' has to be set explicitly.
 */
export function showInternalHints(): boolean {
  const env = appEnvironment();
  if (env === 'preview') return true;
  return env === 'development' && __DEV__;
}
