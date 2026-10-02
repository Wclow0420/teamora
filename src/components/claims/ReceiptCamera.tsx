import React from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { CameraView } from 'expo-camera';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { Icon } from '@/components/ui';
import { captureReceiptBase64, pickReceiptPictureSize, type ReceiptCapture } from '@/lib/receipt';
import { palette, font, onDark, radius, spacing } from '@/theme';

type Props = {
  visible: boolean;
  onClose: () => void;
  /** Receives the outcome of a shutter press (a photo, or why there isn't one). */
  onResult: (result: ReceiptCapture) => void;
};

/**
 * Full-screen back-camera capture step for a claim receipt. Only mounted once
 * camera permission is granted (the caller handles the permission prompt), and
 * always reports back — a failed capture closes with a reason so the claim form
 * can carry on without a photo.
 */
export function ReceiptCamera({ visible, onClose, onResult }: Props) {
  const insets = useSafeAreaInsets();
  const cameraRef = React.useRef<CameraView>(null);
  const [ready, setReady] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [pictureSize, setPictureSize] = React.useState<string | undefined>(undefined);

  React.useEffect(() => {
    if (!visible) {
      setReady(false);
      setBusy(false);
    }
  }, [visible]);

  const onCameraReady = async () => {
    setReady(true);
    // Prefer a moderate still size so the upload stays small; default if unknown.
    try {
      const sizes = await cameraRef.current?.getAvailablePictureSizesAsync();
      if (sizes) setPictureSize(pickReceiptPictureSize(sizes));
    } catch {
      // keep the device default
    }
  };

  const onShutter = async () => {
    if (busy) return;
    setBusy(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    const result = await captureReceiptBase64(cameraRef.current);
    setBusy(false);
    onResult(result);
  };

  return (
    <Modal visible={visible} animationType="slide" statusBarTranslucent onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: palette.espresso }}>
        {visible && (
          <CameraView
            ref={cameraRef}
            facing="back"
            pictureSize={pictureSize}
            onCameraReady={onCameraReady}
            onMountError={() => onResult({ ok: false, reason: 'unavailable' })}
            style={{ flex: 1 }}
          />
        )}

        {/* top bar */}
        <View
          style={{
            position: 'absolute',
            top: insets.top + spacing.sm,
            left: spacing.lg,
            right: spacing.lg,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel="Cancel"
            style={{
              width: 40,
              height: 40,
              borderRadius: radius.md,
              backgroundColor: onDark.scrim,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon name="x" size={18} color={palette.white} />
          </Pressable>
          <View style={{ paddingVertical: 9, paddingHorizontal: 14, borderRadius: radius.pill, backgroundColor: onDark.scrim }}>
            <Text style={[font(600), { fontSize: 12.5, lineHeight: 17, color: palette.white }]}>
              Fit the whole receipt in the frame
            </Text>
          </View>
          <View style={{ width: 40 }} />
        </View>

        {/* shutter */}
        <View
          style={{
            position: 'absolute',
            bottom: insets.bottom + spacing['2xl'],
            left: 0,
            right: 0,
            alignItems: 'center',
          }}
        >
          <Pressable
            onPress={onShutter}
            disabled={busy || !ready}
            accessibilityRole="button"
            accessibilityLabel="Take receipt photo"
            style={{
              width: 76,
              height: 76,
              borderRadius: radius.pill,
              borderWidth: 4,
              borderColor: palette.white,
              alignItems: 'center',
              justifyContent: 'center',
              opacity: busy || !ready ? 0.5 : 1,
            }}
          >
            <View style={{ width: 58, height: 58, borderRadius: radius.pill, backgroundColor: palette.white }} />
          </Pressable>
          <Text style={[font(600), { fontSize: 12, lineHeight: 17, color: palette.white, marginTop: spacing.md }]}>
            {busy ? 'Saving photo…' : ready ? 'Tap to take the photo' : 'Starting camera…'}
          </Text>
        </View>
      </View>
    </Modal>
  );
}
