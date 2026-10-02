import React from 'react';
import { Image, Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon } from '@/components/ui';
import { AuthedImage } from './AuthedImage';
import { palette, font, onDark, radius, spacing } from '@/theme';

type Props = {
  visible: boolean;
  onClose: () => void;
  /** The photo. `authed` URIs are API endpoints that need the Bearer header. */
  uri: string | null;
  authed?: boolean;
  title?: string;
  caption?: string;
};

/** Full-screen photo viewer (receipts). Tap anywhere, or the close button, to dismiss. */
export function PhotoViewer({ visible, onClose, uri, authed = false, title, caption }: Props) {
  const insets = useSafeAreaInsets();
  const imageStyle = { width: '100%', height: '100%' } as const;

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent onRequestClose={onClose}>
      <Pressable
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel="Close photo"
        style={{
          flex: 1,
          backgroundColor: onDark.scrim,
          paddingTop: insets.top + spacing.md,
          paddingBottom: insets.bottom + spacing.lg,
          paddingHorizontal: spacing.lg,
        }}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
          <View style={{ flex: 1, minWidth: 0 }}>
            {!!title && (
              <Text style={[font(700), { fontSize: 15, lineHeight: 20, color: palette.white }]} numberOfLines={1}>
                {title}
              </Text>
            )}
            {!!caption && (
              <Text style={[font(500), { fontSize: 12, lineHeight: 17, color: onDark.text, marginTop: 2 }]} numberOfLines={1}>
                {caption}
              </Text>
            )}
          </View>
          <View
            style={{
              width: 40,
              height: 40,
              borderRadius: radius.md,
              backgroundColor: onDark.control,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon name="x" size={18} color={palette.white} />
          </View>
        </View>

        <View style={{ flex: 1, marginTop: spacing.lg, alignItems: 'center', justifyContent: 'center' }}>
          {uri && authed && (
            <AuthedImage
              uri={uri}
              resizeMode="contain"
              style={imageStyle}
              accessibilityLabel={title}
              fallback={
                <Text style={[font(600), { fontSize: 13, lineHeight: 19, color: onDark.text, textAlign: 'center' }]}>
                  Couldn't load this photo. Close and try again.
                </Text>
              }
            />
          )}
          {uri && !authed && <Image source={{ uri }} resizeMode="contain" accessibilityLabel={title} style={imageStyle} />}
        </View>
      </Pressable>
    </Modal>
  );
}
