import React from 'react';
import { View, ViewStyle, StyleProp, Pressable } from 'react-native';
import { palette, radius, shadows, spacing } from '@/theme';

type Props = {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Inner padding (number → all sides). Defaults to 16. Pass 0 for list cards. */
  padding?: number;
  /** Resting shadow. */
  elevated?: boolean;
  onPress?: () => void;
};

/**
 * The default white surface card: warm hairline border, large radius, optional
 * soft brown elevation. Every card in the app composes from this.
 */
export function Card({ children, style, padding = spacing.lg, elevated = true, onPress }: Props) {
  const base: ViewStyle = {
    backgroundColor: palette.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: palette.line,
    padding,
    ...(elevated ? shadows.card : null),
  };
  if (onPress) {
    return (
      <Pressable onPress={onPress} style={({ pressed }) => [base, pressed && { opacity: 0.9 }, style]}>
        {children}
      </Pressable>
    );
  }
  return <View style={[base, style]}>{children}</View>;
}
