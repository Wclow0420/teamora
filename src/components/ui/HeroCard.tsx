import React from 'react';
import { View, Text, ViewStyle, StyleProp } from 'react-native';
import { palette, font, radius, shadows } from '@/theme';

type Props = {
  /** Small uppercase eyebrow above the amount. */
  eyebrow: string;
  /** The big value, pre-formatted (e.g. "3,502.35"). Rendered with tabular numerals. */
  amount: string;
  /** Leading currency/unit (baseline-aligned), e.g. "RM". */
  currency?: string;
  /** Optional status pill under the amount. */
  pill?: { label: string; dotColor?: string };
  /** Optional trailing node in the top-right (e.g. a small control). */
  trailing?: React.ReactNode;
  amountSize?: number;
  style?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
};

/**
 * The signature espresso "hero" surface — a confident dark card for a headline
 * number (net pay, balance, worked-time). One restrained coral glow, tabular
 * numerals, generous leading. No gradient ramp; depth comes from the surface +
 * a soft float shadow.
 */
export function HeroCard({ eyebrow, amount, currency, pill, trailing, amountSize = 46, style, children }: Props) {
  return (
    <View
      style={[
        {
          backgroundColor: palette.espresso,
          borderRadius: radius.hero,
          padding: 22,
          overflow: 'hidden',
        },
        shadows.float,
        style,
      ]}
    >
      {/* single restrained coral wash, pushed far off-canvas so only a soft arc reads */}
      <View
        style={{
          position: 'absolute',
          right: -84,
          top: -104,
          width: 230,
          height: 230,
          borderRadius: 999,
          backgroundColor: 'rgba(236,106,77,0.10)',
        }}
      />
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <Text style={[font(700), { fontSize: 11, letterSpacing: 1.1, textTransform: 'uppercase', color: 'rgba(255,255,255,0.5)' }]}>
          {eyebrow}
        </Text>
        {trailing}
      </View>

      <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 7, marginTop: 13 }}>
        {currency ? (
          <Text style={[font(700), { fontSize: Math.round(amountSize * 0.42), color: 'rgba(255,255,255,0.82)' }]}>{currency}</Text>
        ) : null}
        <Text
          style={[
            font(800),
            { fontSize: amountSize, lineHeight: amountSize, letterSpacing: -amountSize * 0.038, color: palette.white, fontVariant: ['tabular-nums'] },
          ]}
        >
          {amount}
        </Text>
      </View>

      {pill ? (
        <View style={{ flexDirection: 'row', marginTop: 16 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7, height: 30, paddingHorizontal: 12, borderRadius: radius.pill, backgroundColor: 'rgba(255,255,255,0.10)' }}>
            {pill.dotColor ? <View style={{ width: 6, height: 6, borderRadius: 9, backgroundColor: pill.dotColor }} /> : null}
            <Text style={[font(600), { fontSize: 12, color: '#EBDFD4' }]}>{pill.label}</Text>
          </View>
        </View>
      ) : null}

      {children}
    </View>
  );
}
