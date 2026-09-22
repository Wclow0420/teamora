import { ConfigContext, ExpoConfig } from 'expo/config';

// Single source of truth for app configuration — there is deliberately NO
// app.json; everything lives here so per-environment values (name, package,
// scheme, API URL) can branch on APP_ENV.

// Version comes from package.json so app + build stay in lockstep.
let appVersion = '1.0.0';
try {
  appVersion = require('./package.json').version;
} catch (e) {
  console.warn('Could not read version from package.json:', e);
}

const EAS_PROJECT_ID = 'ce9d1300-4852-4008-bf9b-3e680add6f84';
const OWNER = 'teamora';
const PROJECT_SLUG = 'teamora';

// ── Production identity ───────────────────────────────────────────────────
const PROD_APP_NAME = 'Teamora';
const PROD_PACKAGE_NAME = 'com.teamora.app'; // Android applicationId
const PROD_BUNDLE_IDENTIFIER = 'com.teamora.app'; // iOS
const PROD_SCHEME = 'teamora';

// "Warm & Human" cream — used for the splash + adaptive-icon background.
const BRAND_BG = '#FBF5EE';

type AppEnvironment = 'development' | 'preview' | 'production';

/** Per-environment identity: separate package names so dev/preview/production
 *  installs can live side by side on one device. */
function getDynamicAppConfig(environment: AppEnvironment): {
  name: string;
  packageName: string;
  bundleIdentifier: string;
  scheme: string;
} {
  if (environment === 'production') {
    return {
      name: PROD_APP_NAME,
      packageName: PROD_PACKAGE_NAME,
      bundleIdentifier: PROD_BUNDLE_IDENTIFIER,
      scheme: PROD_SCHEME,
    };
  }
  if (environment === 'preview') {
    return {
      name: `${PROD_APP_NAME} Preview`,
      packageName: `${PROD_PACKAGE_NAME}.preview`,
      bundleIdentifier: `${PROD_BUNDLE_IDENTIFIER}.preview`,
      scheme: `${PROD_SCHEME}-preview`,
    };
  }
  return {
    name: `${PROD_APP_NAME} Dev`,
    packageName: `${PROD_PACKAGE_NAME}.dev`,
    bundleIdentifier: `${PROD_BUNDLE_IDENTIFIER}.dev`,
    scheme: `${PROD_SCHEME}-dev`,
  };
}

export default ({ config }: ConfigContext): ExpoConfig => {
  // Defaults to development, NOT production. Every eas.json profile sets
  // APP_ENV explicitly, so this fallback only ever applies to a local
  // `expo start` — where the dev identity (package .dev, scheme -dev) is what
  // matches an installed dev client.
  const environment = (process.env.APP_ENV as AppEnvironment) || 'development';
  console.log(`⚙️  Building Teamora for environment: ${environment}`);

  const { name, packageName, bundleIdentifier, scheme } = getDynamicAppConfig(environment);

  return {
    ...config,

    name,
    slug: PROJECT_SLUG,
    owner: OWNER,
    version: appVersion,
    orientation: 'portrait',
    scheme,
    userInterfaceStyle: 'light', // Warm & Human is light-mode only (for now).
    newArchEnabled: true,

    splash: {
      backgroundColor: BRAND_BG,
      resizeMode: 'contain',
    },

    ios: {
      supportsTablet: true,
      bundleIdentifier,
      infoPlist: {
        ITSAppUsesNonExemptEncryption: false,
        // Foreground location — used to verify staff are on-site at clock-in.
        NSLocationWhenInUseUsageDescription:
          "Teamora uses your location to verify you're at your work site when you clock in.",
        // Front camera — used to take the clock-in selfie (attendance proof).
        NSCameraUsageDescription: 'Teamora uses the camera to take your clock-in photo.',
      },
    },

    android: {
      package: packageName,
      adaptiveIcon: {
        backgroundColor: BRAND_BG,
      },
      permissions: ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'CAMERA'],
    },

    // Native only. Teamora ships to iOS + Android (internal/TestFlight builds
    // and the stores). Web is deliberately excluded — listing it makes
    // `eas update` export a web bundle too, which fails without react-native-web.
    platforms: ['ios', 'android'],

    plugins: [
      'expo-router',
      'expo-font',
      'expo-asset',
      'expo-secure-store',
      '@react-native-community/datetimepicker',
      'expo-notifications',
      [
        'expo-location',
        {
          // Foreground-only geofence check at clock-in — no background location.
          locationWhenInUsePermission:
            "Teamora uses your location to verify you're at your work site when you clock in.",
          isAndroidBackgroundLocationEnabled: false,
          isAndroidForegroundServiceEnabled: false,
        },
      ],
      [
        'expo-camera',
        {
          // Front-camera clock-in selfie (attendance proof) — foreground only.
          cameraPermission: 'Teamora uses the camera to take your clock-in photo.',
          recordAudioAndroid: false,
        },
      ],
    ],

    experiments: {
      typedRoutes: true,
    },

    extra: {
      APP_ENV: environment,
      router: {},
      // Backend base URL for real builds (no Metro host to infer from).
      // Set per profile in eas.json via EXPO_PUBLIC_API_URL.
      apiUrl: process.env.EXPO_PUBLIC_API_URL,
      eas: {
        projectId: EAS_PROJECT_ID,
      },
    },

    // EAS Update: OTA JS updates per channel (development/preview/production).
    updates: {
      url: `https://u.expo.dev/${EAS_PROJECT_ID}`,
    },
    runtimeVersion: {
      policy: 'appVersion',
    },
  };
};
