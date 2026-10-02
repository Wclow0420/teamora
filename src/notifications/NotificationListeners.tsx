import { useEffect } from 'react';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';
import { useAuth } from '@/context/AuthContext';
import { isAdminRole } from '@/api/types';

/**
 * Keeps the in-app notifications feed live: when a push arrives (foreground) the
 * list refetches; tapping a push opens the Notifications screen of whichever app (staff / admin) the signed-in role lives in. Rendered once
 * at the app root, inside the router + query providers.
 */
export function NotificationListeners() {
  const router = useRouter();
  const qc = useQueryClient();
  const { status, role } = useAuth();
  const signedIn = status === 'authenticated';
  const admin = isAdminRole(role);

  useEffect(() => {
    const received = Notifications.addNotificationReceivedListener(() => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
    });
    const response = Notifications.addNotificationResponseReceivedListener(() => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
      if (!signedIn) return;
      try {
        router.push(admin ? '/admin/notifications' : '/notifications');
      } catch {
        // ignore if the route isn't reachable from the current stack
      }
    });
    return () => {
      received.remove();
      response.remove();
    };
  }, [qc, router, signedIn, admin]);

  return null;
}
