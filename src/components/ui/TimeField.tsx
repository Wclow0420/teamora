import React, { useState } from 'react';
import { View, Text, Pressable, Platform } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { palette, radius, font } from '@/theme';
import { Icon } from './Icon';

type Props = {
  label?: string;
  /** `null` = not set (the field is optional). */
  value: Date | null;
  onChange: (d: Date | null) => void;
  placeholder?: string;
  /** Show the clear (×) action once set. Turn off for a required time. Default true. */
  clearable?: boolean;
};

function format(d: Date): string {
  const h = d.getHours();
  const m = String(d.getMinutes()).padStart(2, '0');
  const suffix = h < 12 ? 'AM' : 'PM';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${m} ${suffix}`;
}

/** Optional time picker field. Mirrors `DateField`, with a clear action once set. */
export function TimeField({ label, value, onChange, placeholder = 'Not set', clearable = true }: Props) {
  const [show, setShow] = useState(false);
  const display = value ? format(value) : placeholder;
  return (
    <View>
      {label && <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 7 }]}>{label}</Text>}
      <Pressable
        onPress={() => setShow((s) => !s)}
        accessibilityRole="button"
        accessibilityLabel={`${label ? `${label}, ` : ''}${display}`}
        accessibilityHint="Opens a time picker"
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
        <Icon name="clock" size={18} color={palette.faint} />
        <Text style={[font(600), { flex: 1, fontSize: 14, color: value ? palette.ink : palette.faint }]}>{display}</Text>
        {value && clearable ? (
          <Pressable
            onPress={() => {
              setShow(false);
              onChange(null);
            }}
            accessibilityRole="button"
            accessibilityLabel="Clear time"
            hitSlop={10}
          >
            <Icon name="x" size={16} color={palette.faint} />
          </Pressable>
        ) : (
          <Icon name="chevD" size={16} color={palette.faint} />
        )}
      </Pressable>
      {show && (
        <View style={Platform.OS === 'ios' ? { alignItems: 'center' } : undefined}>
          <DateTimePicker
            value={value ?? new Date()}
            mode="time"
            display={Platform.OS === 'ios' ? 'spinner' : 'default'}
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

/** Local 24h "HH:mm" for the API. */
export function toHHMM(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** Parse an API "HH:mm" (or "HH:mm:ss") into a Date today at that time; null if it isn't one. */
export function fromHHMM(value: string | null | undefined): Date | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(value ?? '');
  if (!m) return null;
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 23 || min > 59) return null;
  const d = new Date();
  d.setHours(h, min, 0, 0);
  return d;
}
