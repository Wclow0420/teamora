import { useEffect } from 'react';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';

/**
 * Keeps the in-app notifications feed live: when a push arrives (foreground) the
 * list refetches; tapping a push opens the Notifications screen. Rendered once
 * at the app root, inside the router + query providers.
 */
export function NotificationListeners() {
  const router = useRouter();
  const qc = useQueryClient();

  useEffect(() => {
    const received = Notifications.addNotificationReceivedListener(() => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
    });
    const response = Notifications.addNotificationResponseReceivedListener(() => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
      try {
        router.push('/notifications');
      } catch {
        // ignore if the route isn't reachable from the current stack
      }
    });
    return () => {
      received.remove();
      response.remove();
    };
  }, [qc, router]);

  return null;
}
