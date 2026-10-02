import React from 'react';
import { Pressable, type StyleProp, type ViewStyle } from 'react-native';
import { IconTile } from '@/components/ui';
import { AuthedImage } from '@/components/media/AuthedImage';
import { PhotoViewer } from '@/components/media/PhotoViewer';
import { claimApi } from '@/api/endpoints';
import { palette, radius, tint } from '@/theme';

type Props = {
  claimId: string;
  /** Whether the claim has a stored receipt photo. */
  hasReceipt?: boolean;
  size?: number;
  /** Viewer heading, e.g. the claim title. */
  title?: string;
  /** Viewer sub-line, e.g. "RM 42.00 · 3 Oct". */
  caption?: string;
  style?: StyleProp<ViewStyle>;
};

/**
 * A claim's receipt in a list/card slot. With a receipt: the real photo
 * (auth-guarded endpoint), tappable to open it full-screen. Without one — or if
 * the photo can't load — a neutral receipt tile, never a fake picture.
 */
export function ReceiptThumb({ claimId, hasReceipt, size = 46, title, caption, style }: Props) {
  const [open, setOpen] = React.useState(false);
  const corner = radius.md;

  const tile = (
    <IconTile icon="receipt" color={palette.faint} background={tint.neutral} size={size} cornerRadius={corner} style={style} />
  );
  if (!hasReceipt) return tile;

  const uri = claimApi.receiptUrl(claimId);
  return (
    <>
      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="imagebutton"
        accessibilityLabel="View receipt photo"
        hitSlop={6}
        style={style}
      >
        <AuthedImage
          uri={uri}
          fallback={<IconTile icon="receipt" color={palette.faint} background={tint.neutral} size={size} cornerRadius={corner} />}
          style={{
            width: size,
            height: size,
            borderRadius: corner,
            backgroundColor: palette.surfaceSunken,
            borderWidth: 1,
            borderColor: palette.line,
          }}
        />
      </Pressable>
      <PhotoViewer visible={open} onClose={() => setOpen(false)} uri={uri} authed title={title ?? 'Receipt'} caption={caption} />
    </>
  );
}
