import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { notificationApi } from '@/api/endpoints';

/**
 * Expo push notifications. In-app notifications work everywhere; remote *push*
 * delivery requires a development/EAS build — Expo Go (SDK 53+) no longer
 * supports it, so registration is skipped there (and on simulators).
 */

// Show notifications while the app is foregrounded.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

let registeredToken: string | null = null;

function isExpoGo(): boolean {
  return Constants.executionEnvironment === 'storeClient';
}

/** Request permission, get the Expo push token, and register it with the API. */
export async function registerPushToken(): Promise<void> {
  try {
    if (!Device.isDevice || isExpoGo()) return; // no remote push in Expo Go / simulators

    let { status } = await Notifications.getPermissionsAsync();
    if (status !== 'granted') {
      status = (await Notifications.requestPermissionsAsync()).status;
    }
    if (status !== 'granted') return;

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Default',
        importance: Notifications.AndroidImportance.DEFAULT,
      });
    }

    const projectId = Constants.expoConfig?.extra?.eas?.projectId as string | undefined;
    const { data: token } = await Notifications.getExpoPushTokenAsync(projectId ? { projectId } : undefined);
    registeredToken = token;
    await notificationApi.registerPushToken(token, Platform.OS);
  } catch {
    // best-effort — never block the app
  }
}

/** Remove this device's token on sign-out (must run while still authenticated). */
export async function unregisterPushToken(): Promise<void> {
  try {
    if (registeredToken) {
      await notificationApi.removePushToken(registeredToken);
      registeredToken = null;
    }
  } catch {
    // best-effort
  }
}
