import React, { useState } from 'react';
import { View, Text, Pressable, Platform } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { palette, radius, font } from '@/theme';
import { Icon } from './Icon';

type Props = {
  label?: string;
  value: Date;
  onChange: (d: Date) => void;
  minimumDate?: Date;
  maximumDate?: Date;
};

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
function format(d: Date): string {
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

/** Date picker field. Renders the native picker (inline on iOS, dialog on Android). */
export function DateField({ label, value, onChange, minimumDate, maximumDate }: Props) {
  const [show, setShow] = useState(false);
  return (
    <View>
      {label && <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>{label}</Text>}
      <Pressable
        onPress={() => setShow((s) => !s)}
        accessibilityRole="button"
        accessibilityLabel={`${label ? `${label}, ` : ''}${format(value)}`}
        accessibilityHint="Opens a date picker"
        style={{
          height: 52,
          borderRadius: radius.lg,
          backgroundColor: palette.surface,
          borderWidth: 1,
          borderColor: palette.line,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 10,
          paddingHorizontal: 14,
        }}
      >
        <Icon name="calendar" size={18} color={palette.faint} />
        <Text style={[font(600), { flex: 1, fontSize: 14, color: palette.ink }]}>{format(value)}</Text>
        <Icon name="chevD" size={16} color={palette.faint} />
      </Pressable>
      {show && (
        <View style={Platform.OS === 'ios' ? { alignItems: 'center' } : undefined}>
          <DateTimePicker
            value={value}
            mode="date"
            display={Platform.OS === 'ios' ? 'inline' : 'default'}
            minimumDate={minimumDate}
            maximumDate={maximumDate}
            onChange={(event, d) => {
              if (Platform.OS !== 'ios') setShow(false);
              if (event.type === 'set' && d) onChange(d);
            }}
          />
        </View>
      )}
    </View>
  );
}

/** Local YYYY-MM-DD (not UTC-shifted) for the API. */
export function toISODate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}
