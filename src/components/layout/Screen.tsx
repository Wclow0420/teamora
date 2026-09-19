import React from 'react';
import { View, ScrollView, ViewStyle, StyleProp, KeyboardAvoidingView, Platform } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { palette, spacing } from '@/theme';

type Props = {
  children: React.ReactNode;
  /** Wrap content in a vertical ScrollView (default true). */
  scroll?: boolean;
  /** Background colour (defaults to warm cream). */
  background?: string;
  /** Status-bar content colour. */
  barStyle?: 'dark' | 'light';
  /** Horizontal padding (default 20). */
  paddingX?: number;
  /** Extra bottom padding beyond the safe-area (e.g. above a tab bar). */
  bottomInset?: number;
  contentStyle?: StyleProp<ViewStyle>;
};

/**
 * Standard screen frame: safe-area aware, warm background, themed status bar,
 * and an optional scroll container.
 *
 * Keyboard handling is built in (the app-wide standard — see CLAUDE.md): when an
 * input is focused, the screen keeps the focused field above the keyboard. On
 * iOS this uses `automaticallyAdjustKeyboardInsets` (auto inset + scroll to the
 * focused field); on Android a `KeyboardAvoidingView`. Taps outside an input
 * dismiss the keyboard, and dragging the list dismisses it interactively. Never
 * add your own KeyboardAvoidingView in a screen — compose from `Screen`.
 */
export function Screen({
  children,
  scroll = true,
  background = palette.bg,
  barStyle = 'dark',
  paddingX = spacing.screenX,
  bottomInset = 0,
  contentStyle,
}: Props) {
  const insets = useSafeAreaInsets();
  const padTop = insets.top + 6;
  const padBottom = (scroll ? insets.bottom : 0) + bottomInset + 12;

  if (!scroll) {
    return (
      <View style={{ flex: 1, backgroundColor: background, paddingTop: padTop, paddingHorizontal: paddingX, paddingBottom: padBottom }}>
        <StatusBar style={barStyle} />
        {children}
      </View>
    );
  }

  const scrollView = (
    <ScrollView
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="interactive"
      automaticallyAdjustKeyboardInsets
      contentContainerStyle={[{ paddingTop: padTop, paddingHorizontal: paddingX, paddingBottom: padBottom }, contentStyle]}
    >
      {children}
    </ScrollView>
  );

  return (
    <View style={{ flex: 1, backgroundColor: background }}>
      <StatusBar style={barStyle} />
      {Platform.OS === 'android' ? (
        <KeyboardAvoidingView style={{ flex: 1 }} behavior="height">
          {scrollView}
        </KeyboardAvoidingView>
      ) : (
        scrollView
      )}
    </View>
  );
}
