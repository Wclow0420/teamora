import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { palette, radius, font } from '@/theme';

export type SelectOption<T extends string> = { value: T; label: string };

type Props<T extends string> = {
  label?: string;
  options: SelectOption<T>[];
  value: T;
  onChange: (value: T) => void;
};

/** Single-choice chip group (wraps). The selected chip is ink-filled. */
export function SelectChips<T extends string>({ label, options, value, onChange }: Props<T>) {
  return (
    <View>
      {label && <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 8 }]}>{label}</Text>}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {options.map((opt) => {
          const active = opt.value === value;
          return (
            <Pressable
              key={opt.value}
              onPress={() => onChange(opt.value)}
              style={{
                paddingVertical: 9,
                paddingHorizontal: 14,
                borderRadius: radius.pill,
                backgroundColor: active ? palette.ink : palette.surface,
                borderWidth: active ? 0 : 1,
                borderColor: palette.line,
              }}
            >
              <Text style={[font(700), { fontSize: 12.5, color: active ? palette.white : palette.soft }]}>{opt.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
