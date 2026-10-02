import React from 'react';
import { Image, type ImageResizeMode, type ImageStyle, type StyleProp } from 'react-native';
import { authApi } from '@/api/endpoints';
import { getAccessToken } from '@/api/tokenStore';

type Props = {
  /** Absolute URL of an auth-guarded image endpoint (e.g. `claimApi.receiptUrl(id)`). */
  uri: string;
  style?: StyleProp<ImageStyle>;
  resizeMode?: ImageResizeMode;
  /** Rendered when there's no session token or the image fails to load. */
  fallback?: React.ReactNode;
  accessibilityLabel?: string;
};

/**
 * `<Image>` for the API's auth-guarded photo endpoints (clock-in selfies, claim
 * receipts): attaches the Bearer token as a request header and swaps to
 * `fallback` if there's no token or the load still fails after a token refresh — so a row never shows a
 * broken image.
 */
export function AuthedImage({ uri, style, resizeMode = 'cover', fallback = null, accessibilityLabel }: Props) {
  const [failed, setFailed] = React.useState(false);
  // Bumped after a token refresh so the image re-requests with the new token.
  const [attempt, setAttempt] = React.useState(0);
  const token = getAccessToken();

  // A new image gets a fresh chance to load.
  React.useEffect(() => {
    setFailed(false);
    setAttempt(0);
  }, [uri]);

  /**
   * `<Image>` can't refresh an expired access token itself. On the first
   * failure, make one API call (the client refreshes the token on a 401) and
   * try again; a second failure is a real one.
   */
  const onError = () => {
    if (attempt > 0) {
      setFailed(true);
      return;
    }
    authApi
      .me()
      .then(() => setAttempt(1))
      .catch(() => setFailed(true));
  };

  if (!token || failed) return <>{fallback}</>;

  return (
    <Image
      key={attempt}
      source={{ uri, headers: { Authorization: `Bearer ${token}` } }}
      onError={onError}
      resizeMode={resizeMode}
      accessibilityLabel={accessibilityLabel}
      style={style}
    />
  );
}
