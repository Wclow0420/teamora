import { useEffect } from 'react';
import { AppState } from 'react-native';
import { usePathname } from 'expo-router';
import { focusManager } from '@tanstack/react-query';
import { queryClient } from './queryClient';

/**
 * Keeps on-screen data fresh without per-screen wiring. React Query's "window
 * focus" has no meaning in React Native, so nothing refetched unless a screen
 * remounted — an approval or payroll status changed on another device stayed
 * stale indefinitely. Two triggers, both limited to queries that are mounted
 * and past their `staleTime`:
 *  - the app returning to the foreground (AppState → React Query focus);
 *  - navigating to another route (tab switch, push, back).
 */
export function QueryFreshness() {
  const pathname = usePathname();

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      focusManager.setFocused(state === 'active');
    });
    return () => sub.remove();
  }, []);

  useEffect(() => {
    void queryClient.refetchQueries({ type: 'active', stale: true });
  }, [pathname]);

  return null;
}
