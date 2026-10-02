import React from 'react';
import { Pressable, StyleProp, ViewStyle } from 'react-native';
import { Avatar } from '@/components/ui';
import { attendanceApi } from '@/api/endpoints';
import { AuthedImage } from '@/components/media/AuthedImage';
import { palette } from '@/theme';

type Props = {
  /** Today's attendance record id — required to fetch the selfie. */
  recordId?: string | null;
  /** Whether the record actually has a stored selfie. */
  hasPhoto?: boolean;
  /** Fallback avatar initial when there's no photo (or it fails to load). */
  initial: string;
  /** Whose photo this is — names the tap target for screen readers. */
  name?: string;
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
export function SelfieThumb({ recordId, hasPhoto, initial, name, tint, size = 38, onPress, style }: Props) {
  const avatar = <Avatar initial={initial} size={size} tint={tint} />;

  if (!hasPhoto || !recordId) {
    return <Avatar initial={initial} size={size} tint={tint} style={style} />;
  }

  const corner = Math.round(size * 0.34);
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole="imagebutton"
      accessibilityLabel={name ? `View ${name}'s clock-in photo` : 'View clock-in photo'}
      style={style}
    >
      <AuthedImage
        uri={attendanceApi.photoUrl(recordId)}
        fallback={avatar}
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
