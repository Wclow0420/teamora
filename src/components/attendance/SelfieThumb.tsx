import React from 'react';
import { Image, Pressable, StyleProp, ViewStyle } from 'react-native';
import { Avatar } from '@/components/ui';
import { attendanceApi } from '@/api/endpoints';
import { getAccessToken } from '@/api/tokenStore';
import { palette } from '@/theme';

type Props = {
  /** Today's attendance record id — required to fetch the selfie. */
  recordId?: string | null;
  /** Whether the record actually has a stored selfie. */
  hasPhoto?: boolean;
  /** Fallback avatar initial when there's no photo (or it fails to load). */
  initial: string;
  /** Fallback avatar tint. */
  tint: { bg: string; fg: string };
  size?: number;
  /** Tap handler (only wired when a photo is shown). */
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
};

/**
 * Clock-in selfie thumbnail for the admin Live board. Loads the auth-guarded
 * photo endpoint with a Bearer header; falls back to the tinted initial
 * {@link Avatar} when there's no photo, no session token, or the image fails to
 * load — so the row is always resilient.
 */
export function SelfieThumb({ recordId, hasPhoto, initial, tint, size = 38, onPress, style }: Props) {
  const [failed, setFailed] = React.useState(false);
  const token = getAccessToken();

  const showPhoto = !!hasPhoto && !!recordId && !!token && !failed;

  if (!showPhoto) {
    return <Avatar initial={initial} size={size} tint={tint} style={style} />;
  }

  const corner = Math.round(size * 0.34);
  return (
    <Pressable onPress={onPress} disabled={!onPress} style={style}>
      <Image
        source={{
          uri: attendanceApi.photoUrl(recordId),
          headers: { Authorization: `Bearer ${token}` },
        }}
        onError={() => setFailed(true)}
        style={{
          width: size,
          height: size,
          borderRadius: corner,
          backgroundColor: palette.bg,
          borderWidth: 1,
          borderColor: palette.line,
        }}
      />
    </Pressable>
  );
}
