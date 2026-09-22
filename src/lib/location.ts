import * as Location from 'expo-location';

/** A captured device position, in decimal degrees. */
export type Coords = { latitude: number; longitude: number };

/**
 * Why a location capture failed:
 * - `denied`      — the user declined the foreground location permission.
 * - `unavailable` — permission is granted but no fix could be obtained (GPS off,
 *   indoors, timeout, or a device error).
 */
export type LocationErrorKind = 'denied' | 'unavailable';

export class LocationError extends Error {
  kind: LocationErrorKind;
  constructor(kind: LocationErrorKind, message: string) {
    super(message);
    this.name = 'LocationError';
    this.kind = kind;
  }
}

/** How long we wait for a single fix before giving up (ms). */
const FIX_TIMEOUT_MS = 12_000;

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new LocationError('unavailable', 'Timed out getting your location.')),
      ms,
    );
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e) => {
        clearTimeout(timer);
        reject(e);
      },
    );
  });
}

/**
 * Request foreground location permission (if needed) and return the device's
 * current position. Throws a typed {@link LocationError} — `denied` when the
 * user refuses the permission, `unavailable` when a fix can't be obtained.
 */
export async function getCurrentCoords(): Promise<Coords> {
  let status: Location.PermissionStatus;
  try {
    const perm = await Location.requestForegroundPermissionsAsync();
    status = perm.status;
  } catch {
    throw new LocationError('unavailable', 'Could not request location permission.');
  }
  if (status !== 'granted') {
    throw new LocationError('denied', 'Location permission was not granted.');
  }

  try {
    const pos = await withTimeout(
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
      FIX_TIMEOUT_MS,
    );
    return { latitude: pos.coords.latitude, longitude: pos.coords.longitude };
  } catch (e) {
    if (e instanceof LocationError) throw e;
    throw new LocationError('unavailable', 'Could not determine your location. Please try again.');
  }
}
