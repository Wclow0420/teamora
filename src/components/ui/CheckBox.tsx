import React from 'react';
import { View } from 'react-native';
import { palette, radius } from '@/theme';
import { Icon } from './Icon';

type Props = {
  checked: boolean;
  /** Dimmed and shown unticked-looking (e.g. a row that's already been added). */
  disabled?: boolean;
};

/**
 * The tick box for list rows. Purely visual — put it inside a `Pressable` row
 * that carries `accessibilityRole="checkbox"` and the checked state.
 */
export function CheckBox({ checked, disabled = false }: Props) {
  const on = checked && !disabled;
  return (
    <View
      style={{
        width: 22,
        height: 22,
        borderRadius: radius.sm - 2,
        borderWidth: on ? 0 : 1.5,
        borderColor: palette.line,
        backgroundColor: on ? palette.coral : disabled ? palette.surfaceSunken : palette.surface,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {on && <Icon name="check" size={14} color={palette.white} stroke={2.6} />}
    </View>
  );
}
