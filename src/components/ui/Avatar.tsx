import React from 'react';
import { Text, View, ViewStyle, StyleProp } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { palette, radius, font, gradients } from '@/theme';

type Props = {
  /** Single initial or short label. */
  initial: string;
  size?: number;
  /** Gradient pair (defaults to coral avatar gradient). */
  colors?: readonly [string, string, ...string[]];
  /** Show a small online dot in the top-right. */
  online?: boolean;
  /** Solid tint variant instead of a gradient (used in dense lists). */
  tint?: { bg: string; fg: string };
  style?: StyleProp<ViewStyle>;
};

/** Gradient or tinted initial avatar — the prototype's signature identity chip. */
export function Avatar({ initial, size = 46, colors = gradients.avatar, online = false, tint, style }: Props) {
  const corner = Math.round(size * 0.34);
  const fontSize = Math.round(size * 0.37);

  const inner = (
    <Text style={[font(800), { color: tint ? tint.fg : palette.white, fontSize }]}>{initial}</Text>
  );

  return (
    <View style={[{ width: size, height: size }, style]}>
      {tint ? (
        <View style={{ width: size, height: size, borderRadius: corner, backgroundColor: tint.bg, alignItems: 'center', justifyContent: 'center' }}>
          {inner}
        </View>
      ) : (
        <LinearGradient colors={colors} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ width: size, height: size, borderRadius: corner, alignItems: 'center', justifyContent: 'center' }}>
          {inner}
        </LinearGradient>
      )}
      {online && (
        <View
          style={{
            position: 'absolute',
            top: -3,
            right: -3,
            width: 11,
            height: 11,
            borderRadius: radius.pill,
            backgroundColor: palette.coral,
            borderWidth: 2,
            borderColor: palette.bg,
          }}
        />
      )}
    </View>
  );
}
