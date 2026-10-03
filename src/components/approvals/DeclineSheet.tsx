import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Keyboard, Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button, TextField } from '@/components/ui';
import { palette, font, onDark, radius, spacing, shadows } from '@/theme';

/** Server-side cap on `decision_note`. */
const MAX_REASON = 300;

type Props = {
  /** Open the sheet. */
  visible: boolean;
  /** What is being declined, e.g. "leave request", "claim". */
  what: string;
  /** The reject request is in flight — buttons lock, the sheet stays open. */
  pending?: boolean;
  /** Last failure, shown inline so the approver can retry or cancel. */
  error?: string | null;
  onCancel: () => void;
  /** Called with the trimmed reason ('' when none was typed). */
  onConfirm: (reason: string) => void;
};

/**
 * Bottom sheet that confirms a Decline and lets the approver add an optional
 * reason the employee will see. Built on RN `Modal` (works the same on iOS and
 * Android — no `Alert.prompt`).
 *
 * Keyboard: `Screen` owns keyboard handling for screens, but it can't wrap a
 * Modal (a separate native window), so this sheet carries its own
 * `KeyboardAvoidingView`. `padding` on both platforms is self-correcting: it
 * pads only by however much the keyboard still overlaps the sheet, so it's
 * right whether or not Android's adjustResize already shrank the window.
 */
export function DeclineSheet({ visible, what, pending = false, error, onCancel, onConfirm }: Props) {
  const insets = useSafeAreaInsets();
  const [reason, setReason] = useState('');

  // A fresh sheet every time it opens — never carry one request's reason into the next.
  useEffect(() => {
    if (visible) setReason('');
  }, [visible]);

  const close = () => {
    if (pending) return;
    Keyboard.dismiss();
    onCancel();
  };

  const remaining = MAX_REASON - reason.length;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      statusBarTranslucent
      navigationBarTranslucent
      onRequestClose={close}
    >
      <KeyboardAvoidingView behavior="padding" style={{ flex: 1, backgroundColor: onDark.backdrop }}>
        {/* Tap above the sheet to cancel. */}
        <Pressable style={{ flex: 1 }} onPress={close} accessibilityRole="button" accessibilityLabel="Cancel" />

        <View
          style={[
            {
              backgroundColor: palette.surface,
              borderTopLeftRadius: radius['3xl'],
              borderTopRightRadius: radius['3xl'],
              paddingTop: spacing.md,
              paddingHorizontal: spacing.screenX,
              paddingBottom: insets.bottom + spacing.lg,
            },
            shadows.float,
          ]}
        >
          {/* grab handle */}
          <View
            style={{
              alignSelf: 'center',
              width: 40,
              height: 4,
              borderRadius: radius.pill,
              backgroundColor: palette.line,
              marginBottom: spacing.lg,
            }}
          />

          <Text style={[font(800), { fontSize: 18, lineHeight: 23, letterSpacing: -0.3, color: palette.ink }]}>
            Decline this {what}?
          </Text>
          <Text style={[font(500), { fontSize: 13, lineHeight: 19, color: palette.soft, marginTop: 6, marginBottom: spacing.lg }]}>
            They'll be notified, and this can't be undone.
          </Text>

          <TextField
            label="Reason (optional)"
            value={reason}
            onChangeText={setReason}
            placeholder="Let them know why — they'll see this"
            multiline
            maxLength={MAX_REASON}
            error={error}
            helper={remaining <= 50 ? `${remaining} characters left` : undefined}
          />

          <View style={{ flexDirection: 'row', gap: 10, marginTop: spacing.xl }}>
            {/* Button is full-width by default — the flex share lives on the wrappers. */}
            <View style={{ flex: 1 }}>
              <Button label="Cancel" variant="ghost" height={48} disabled={pending} onPress={close} />
            </View>
            <View style={{ flex: 1.4 }}>
              <Button
                label={pending ? 'Declining…' : 'Decline'}
                variant="danger"
                icon="x"
                height={48}
                disabled={pending}
                onPress={() => {
                  Keyboard.dismiss();
                  onConfirm(reason.trim());
                }}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
