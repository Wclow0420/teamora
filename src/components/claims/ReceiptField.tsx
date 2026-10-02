import React from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import { useCameraPermissions } from 'expo-camera';
import { Card, Icon, IconTile } from '@/components/ui';
import { PhotoViewer } from '@/components/media/PhotoViewer';
import { receiptDataUri, type ReceiptCapture } from '@/lib/receipt';
import { ReceiptCamera } from './ReceiptCamera';
import { palette, font, radius, spacing, tint } from '@/theme';

type Props = {
  /** Captured receipt as bare base64 JPEG, or null when none is attached. */
  value: string | null;
  onChange: (base64: string | null) => void;
  disabled?: boolean;
};

const NOTE_DENIED =
  "Camera access is off, so this claim will go in without a photo. To attach one, allow Camera for Teamora in your phone's Settings.";
const NOTE_UNAVAILABLE =
  "We couldn't take a photo on this device. You can still submit the claim without a receipt.";
const NOTE_TOO_LARGE =
  'That photo came out too large to upload. Try again a little further from the receipt, or submit without one.';

/**
 * "Add receipt photo" form field for a claim. Opens the back camera, shows the
 * captured thumbnail with Retake / Remove, and never blocks the form: if the
 * camera is denied or unavailable it explains why and the claim can go in
 * without a photo.
 */
export function ReceiptField({ value, onChange, disabled = false }: Props) {
  const [permission, requestPermission] = useCameraPermissions();
  const [cameraOpen, setCameraOpen] = React.useState(false);
  const [viewerOpen, setViewerOpen] = React.useState(false);
  const [note, setNote] = React.useState<string | null>(null);

  const openCamera = async () => {
    if (disabled) return;
    setNote(null);
    try {
      let granted = permission?.granted ?? false;
      if (!granted && (permission?.canAskAgain ?? true)) {
        granted = (await requestPermission()).granted;
      }
      if (!granted) {
        setNote(NOTE_DENIED);
        return;
      }
      setCameraOpen(true);
    } catch {
      setNote(NOTE_UNAVAILABLE);
    }
  };

  const onResult = (result: ReceiptCapture) => {
    setCameraOpen(false);
    if (result.ok) {
      onChange(result.base64);
      return;
    }
    setNote(result.reason === 'too-large' ? NOTE_TOO_LARGE : NOTE_UNAVAILABLE);
  };

  const uri = value ? receiptDataUri(value) : null;

  return (
    <View>
      <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>Receipt photo (optional)</Text>

      {uri ? (
        <Card padding={spacing.md} elevated={false} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, borderRadius: radius.lg }}>
          <Pressable onPress={() => setViewerOpen(true)} accessibilityRole="imagebutton" accessibilityLabel="View receipt photo">
            <Image
              source={{ uri }}
              style={{
                width: 64,
                height: 64,
                borderRadius: radius.md,
                backgroundColor: palette.surfaceSunken,
                borderWidth: 1,
                borderColor: palette.line,
              }}
            />
          </Pressable>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Icon name="check" size={15} color={palette.sage} stroke={2.2} />
              <Text style={[font(700), { fontSize: 13.5, lineHeight: 18, color: palette.ink }]}>Receipt attached</Text>
            </View>
            <View style={{ flexDirection: 'row', gap: spacing.lg, marginTop: 6 }}>
              <Pressable onPress={openCamera} disabled={disabled} accessibilityRole="button" hitSlop={8} style={{ paddingVertical: 4 }}>
                <Text style={[font(700), { fontSize: 12.5, lineHeight: 17, color: palette.coral }]}>Retake</Text>
              </Pressable>
              <Pressable
                onPress={() => {
                  setNote(null);
                  onChange(null);
                }}
                disabled={disabled}
                accessibilityRole="button"
                hitSlop={8}
                style={{ paddingVertical: 4 }}
              >
                <Text style={[font(700), { fontSize: 12.5, lineHeight: 17, color: palette.danger }]}>Remove</Text>
              </Pressable>
            </View>
          </View>
        </Card>
      ) : (
        <Card
          padding={spacing.md}
          elevated={false}
          onPress={openCamera}
          style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, borderRadius: radius.lg }}
        >
          <IconTile icon="camera" color={palette.coral} background={tint.coral} size={44} />
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={[font(700), { fontSize: 13.5, lineHeight: 18, color: palette.ink }]}>Add receipt photo</Text>
            <Text style={[font(500), { fontSize: 11.5, lineHeight: 16, color: palette.faint, marginTop: 3 }]}>
              Helps your approver check the claim faster.
            </Text>
          </View>
          <Icon name="chevR" size={16} color={palette.faint} />
        </Card>
      )}

      {note && (
        <Text style={[font(500), { fontSize: 11.5, lineHeight: 16, color: palette.soft, marginTop: 7 }]}>{note}</Text>
      )}

      <ReceiptCamera visible={cameraOpen} onClose={() => setCameraOpen(false)} onResult={onResult} />
      <PhotoViewer visible={viewerOpen} onClose={() => setViewerOpen(false)} uri={uri} title="Receipt photo" caption="Tap anywhere to close" />
    </View>
  );
}
