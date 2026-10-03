import React from 'react';
import { View, Text, Pressable } from 'react-native';
import * as Haptics from 'expo-haptics';
import { palette, radius, type, tint, shadows } from '@/theme';
import { Icon, IconName } from './Icon';
import { IconTile } from './IconTile';

type Props = {
  icon: IconName;
  title: string;
  /** One plain line under the title. */
  subtitle?: string;
  selected: boolean;
  onPress: () => void;
  /** `radio` (default) for one-of-many, `checkbox` for multi-select. */
  mode?: 'radio' | 'checkbox';
  /** Icon accent while selected (unselected tiles stay neutral). Defaults to coral. */
  accent?: { color: string; bg: string };
  disabled?: boolean;
};

/**
 * A big, calm, tappable option card — icon, title, one line of explanation and a
 * check. For "pick one" questions where each answer deserves a sentence (e.g.
 * "One office" vs "Anywhere"). Stack them with a gap; they're full-width.
 */
export function ChoiceTile({
  icon,
  title,
  subtitle,
  selected,
  onPress,
  mode = 'radio',
  accent = { color: palette.coral, bg: tint.coral },
  disabled = false,
}: Props) {
  const round = mode === 'radio';
  return (
    <Pressable
      onPress={() => {
        Haptics.selectionAsync().catch(() => {});
        onPress();
      }}
      disabled={disabled}
      accessibilityRole={round ? 'radio' : 'checkbox'}
      accessibilityLabel={subtitle ? `${title}. ${subtitle}` : title}
      accessibilityState={round ? { selected, disabled } : { checked: selected, disabled }}
      style={({ pressed }) => [
        {
          flexDirection: 'row',
          alignItems: 'center',
          gap: 14,
          padding: 16,
          borderRadius: radius.xl,
          backgroundColor: palette.surface,
          // Same border width in both states so the content never shifts on select.
          borderWidth: 1.5,
          borderColor: selected ? palette.coral : palette.line,
          opacity: disabled ? 0.5 : 1,
          transform: [{ scale: pressed ? 0.985 : 1 }],
        },
        shadows.card,
      ]}
    >
      <IconTile
        icon={icon}
        color={selected ? accent.color : palette.soft}
        background={selected ? accent.bg : tint.neutral}
        size={44}
        iconSize={22}
        cornerRadius={radius.md + 2}
      />
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={[type.title, { color: palette.ink }]}>{title}</Text>
        {!!subtitle && (
          <Text style={[type.meta, { fontSize: 12.5, lineHeight: 18, color: palette.soft, marginTop: 3 }]}>{subtitle}</Text>
        )}
      </View>
      <View
        style={{
          width: 24,
          height: 24,
          borderRadius: round ? radius.pill : radius.sm - 1,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: selected ? palette.coral : palette.surface,
          borderWidth: selected ? 0 : 1.5,
          borderColor: palette.line,
        }}
      >
        {selected && <Icon name="check" size={14} color={palette.white} stroke={2.8} />}
      </View>
    </Pressable>
  );
}
