import React from 'react';
import { Pressable, Text, View, ViewStyle, StyleProp, GestureResponderEvent } from 'react-native';
import * as Haptics from 'expo-haptics';
import { palette, radius, font, shadows } from '@/theme';
import { Icon, IconName } from './Icon';

type Variant = 'primary' | 'dark' | 'light' | 'ghost' | 'success';

type Props = {
  label: string;
  onPress?: (e: GestureResponderEvent) => void;
  variant?: Variant;
  icon?: IconName;
  /** Place the icon after the label instead of before. */
  iconTrailing?: boolean;
  /** Full-width (default true) or hug contents. */
  block?: boolean;
  height?: number;
  disabled?: boolean;
  /** Fire a light haptic on press. Default true. */
  haptic?: boolean;
  style?: StyleProp<ViewStyle>;
};

type Skin = { bg: string; fg: string; pressedBg: string; border?: string; shadow?: object };

/**
 * The one button to rule them all — confident flat colour, no gradient. Depth
 * comes from a soft ambient shadow and a crafted press (subtle scale + a darker
 * pressed fill), never from a glow.
 *   primary → coral solid CTA
 *   dark    → ink solid (secondary CTA)
 *   light   → white on cream, hairline border (tertiary)
 *   ghost   → transparent / text only
 *   success → sage solid (approve actions)
 */
const SKINS: Record<Variant, Skin> = {
  primary: { bg: palette.coral, fg: palette.white, pressedBg: '#D9502F', shadow: shadows.coral },
  dark: { bg: palette.ink, fg: palette.white, pressedBg: '#20180F' },
  success: { bg: palette.sage, fg: palette.white, pressedBg: '#4E7C60' },
  light: { bg: palette.surface, fg: palette.coral, pressedBg: '#FBF3EC', border: palette.line, shadow: shadows.card },
  ghost: { bg: 'transparent', fg: palette.soft, pressedBg: 'rgba(44,38,32,0.05)' },
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  icon,
  iconTrailing = false,
  block = true,
  height = 52,
  disabled = false,
  haptic = true,
  style,
}: Props) {
  const s = SKINS[variant];

  const handlePress = (e: GestureResponderEvent) => {
    if (disabled) return;
    if (haptic) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onPress?.(e);
  };

  return (
    <Pressable onPress={handlePress} disabled={disabled} style={block ? { width: '100%' } : undefined}>
      {({ pressed }) => (
        <View
          style={[
            {
              height,
              width: block ? '100%' : undefined,
              borderRadius: radius.lg,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              paddingHorizontal: 18,
              backgroundColor: pressed && !disabled ? s.pressedBg : s.bg,
              opacity: disabled ? 0.5 : 1,
              transform: [{ scale: pressed && !disabled ? 0.97 : 1 }],
            },
            s.border ? { borderWidth: 1, borderColor: s.border } : null,
            !disabled && s.shadow ? s.shadow : null,
            style,
          ]}
        >
          {icon && !iconTrailing && <Icon name={icon} size={19} color={s.fg} stroke={2.2} />}
          <Text style={[font(700), { fontSize: 15, letterSpacing: -0.2, color: s.fg }]} numberOfLines={1}>
            {label}
          </Text>
          {icon && iconTrailing && <Icon name={icon} size={19} color={s.fg} stroke={2.2} />}
        </View>
      )}
    </Pressable>
  );
}
