import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { palette, radius, font } from '@/theme';
import { WEEKDAYS, isBitSet, toggleBit } from '@/lib/workweek';

type Props = {
  label?: string;
  /** Working-weekday bitmask (bit0=Mon … bit6=Sun). */
  value: number;
  onChange: (mask: number) => void;
  /** Inline validation message (e.g. no day selected). */
  error?: string | null;
  /** Read-only: the days are shown dimmed and can't be toggled. */
  disabled?: boolean;
};

/**
 * Seven Mon–Sun toggles that read/write a weekday bitmask. Selected days are
 * ink-filled (working days); unselected are rest days.
 */
export function WeekdayToggles({ label, value, onChange, error, disabled = false }: Props) {
  return (
    <View>
      {label && <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 8 }]}>{label}</Text>}
      <View style={{ flexDirection: 'row', gap: 6, opacity: disabled ? 0.45 : 1 }}>
        {WEEKDAYS.map((w) => {
          const on = isBitSet(value, w.bit);
          return (
            <Pressable
              key={w.bit}
              onPress={() => onChange(toggleBit(value, w.bit))}
              disabled={disabled}
              accessibilityRole="switch"
              accessibilityLabel={w.label}
              accessibilityState={{ checked: on, disabled }}
              style={{
                flex: 1,
                paddingVertical: 11,
                borderRadius: radius.md,
                alignItems: 'center',
                backgroundColor: on ? palette.ink : palette.surface,
                borderWidth: on ? 0 : 1,
                borderColor: error ? palette.danger : palette.line,
              }}
            >
              <Text style={[font(700), { fontSize: 11.5, color: on ? palette.white : palette.faint }]}>
                {w.short.charAt(0)}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {error && <Text style={[font(600), { fontSize: 11.5, lineHeight: 16, color: palette.danger, marginTop: 6 }]}>{error}</Text>}
    </View>
  );
}
