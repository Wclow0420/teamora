import React from 'react';
import { Image, type ImageResizeMode, type ImageStyle, type StyleProp } from 'react-native';
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
 * `fallback` if there's no token or the load fails — so a row never shows a
 * broken image.
 */
export function AuthedImage({ uri, style, resizeMode = 'cover', fallback = null, accessibilityLabel }: Props) {
  const [failed, setFailed] = React.useState(false);
  const token = getAccessToken();

  // A new image gets a fresh chance to load.
  React.useEffect(() => setFailed(false), [uri]);

  if (!token || failed) return <>{fallback}</>;

  return (
    <Image
      source={{ uri, headers: { Authorization: `Bearer ${token}` } }}
      onError={() => setFailed(true)}
      resizeMode={resizeMode}
      accessibilityLabel={accessibilityLabel}
      style={style}
    />
  );
}
