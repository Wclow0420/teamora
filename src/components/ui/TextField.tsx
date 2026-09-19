import React from 'react';
import { View, Text, TextInput, KeyboardTypeOptions } from 'react-native';
import { palette, radius, font } from '@/theme';
import { Icon, IconName } from './Icon';

type Props = {
  label?: string;
  icon?: IconName;
  value: string;
  onChangeText: (t: string) => void;
  placeholder?: string;
  secure?: boolean;
  keyboardType?: KeyboardTypeOptions;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  multiline?: boolean;
  error?: string | null;
};

/** Labelled text input matching the Warm & Human form style. */
export function TextField({
  label,
  icon,
  value,
  onChangeText,
  placeholder,
  secure,
  keyboardType,
  autoCapitalize = 'sentences',
  multiline = false,
  error,
}: Props) {
  return (
    <View>
      {label && <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>{label}</Text>}
      <View
        style={{
          minHeight: multiline ? 88 : 52,
          borderRadius: radius.lg,
          backgroundColor: palette.surface,
          borderWidth: 1,
          borderColor: error ? palette.danger : palette.line,
          flexDirection: 'row',
          alignItems: multiline ? 'flex-start' : 'center',
          gap: 10,
          paddingHorizontal: 14,
          paddingVertical: multiline ? 12 : 0,
        }}
      >
        {icon && <Icon name={icon} size={18} color={palette.faint} />}
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={palette.faint}
          secureTextEntry={secure}
          keyboardType={keyboardType}
          autoCapitalize={autoCapitalize}
          autoCorrect={false}
          multiline={multiline}
          style={[font(500), { flex: 1, fontSize: 14, color: palette.ink, paddingTop: multiline ? 0 : undefined, textAlignVertical: multiline ? 'top' : 'center' }]}
        />
      </View>
      {error && <Text style={[font(600), { fontSize: 11.5, color: palette.danger, marginTop: 6 }]}>{error}</Text>}
    </View>
  );
}
