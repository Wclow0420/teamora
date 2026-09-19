import React from 'react';
import { View, ViewStyle, StyleProp } from 'react-native';
import { radius } from '@/theme';
import { Icon, IconName } from './Icon';

type Props = {
  icon: IconName;
  /** Icon colour (accent). */
  color: string;
  /** Tile background (paired tint). */
  background: string;
  size?: number;
  iconSize?: number;
  cornerRadius?: number;
  style?: StyleProp<ViewStyle>;
};

/**
 * The rounded tinted square that holds an icon — used in quick-actions, list
 * leading slots, breakdown rows and feed items throughout the app.
 */
export function IconTile({ icon, color, background, size = 40, iconSize, cornerRadius, style }: Props) {
  return (
    <View
      style={[
        {
          width: size,
          height: size,
          borderRadius: cornerRadius ?? Math.round(size * 0.3),
          backgroundColor: background,
          alignItems: 'center',
          justifyContent: 'center',
        },
        style,
      ]}
    >
      <Icon name={icon} size={iconSize ?? Math.round(size * 0.47)} color={color} />
    </View>
  );
}
