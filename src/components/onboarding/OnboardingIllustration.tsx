import React from 'react';
import { View, Text, type TextStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Icon, IconTile } from '@/components/ui';
import { palette, font, radius, gradients, shadows, tint, onDark, type } from '@/theme';

const NUM: TextStyle = { fontVariant: ['tabular-nums'] };

/**
 * Onboarding hero art — a small stylised preview of the product, composed from
 * the theme (no image assets): the espresso worked-time card, an approved-leave
 * chip and a payslip row, loosely stacked on the warm cream hero surface.
 * Purely decorative, so it is hidden from screen readers.
 */
export function OnboardingIllustration() {
  return (
    <LinearGradient
      colors={gradients.creamSoft}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      accessible={false}
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
      style={{ height: 224, borderRadius: radius.hero, borderWidth: 1, borderColor: palette.line, overflow: 'hidden' }}
    >
      {/* soft sun + ground shapes for depth */}
      <View style={{ position: 'absolute', right: -46, top: -58, width: 190, height: 190, borderRadius: radius.pill, backgroundColor: tint.amber }} />
      <View style={{ position: 'absolute', left: -70, bottom: -110, width: 230, height: 230, borderRadius: radius.pill, backgroundColor: tint.coral }} />

      {/* worked-time card */}
      <View
        style={[
          {
            position: 'absolute',
            left: 18,
            top: 26,
            width: '58%',
            backgroundColor: palette.espresso,
            borderRadius: radius['3xl'],
            padding: 16,
            overflow: 'hidden',
            transform: [{ rotate: '-4deg' }],
          },
          shadows.float,
        ]}
      >
        <View style={{ position: 'absolute', right: -60, top: -70, width: 150, height: 150, borderRadius: radius.pill, backgroundColor: onDark.glow }} />
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7 }}>
          <View style={{ width: 7, height: 7, borderRadius: radius.pill, backgroundColor: onDark.live }} />
          <Text numberOfLines={1} style={[type.eyebrow, { fontSize: 9.5, color: onDark.eyebrow }]}>Worked today</Text>
        </View>
        <Text style={[font(800), { fontSize: 34, lineHeight: 41, letterSpacing: -1.1, color: palette.white, marginTop: 6 }, NUM]}>
          8:00
          <Text style={[font(800), { fontSize: 18, color: onDark.dim, letterSpacing: -0.4 }]}>:00</Text>
        </Text>
        <View style={{ height: 30, borderRadius: radius.md, backgroundColor: palette.surface, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 6, marginTop: 10 }}>
          <Icon name="clock" size={14} color={palette.ink} />
          <Text style={[font(700), { fontSize: 11.5, color: palette.ink }]}>Clock Out</Text>
        </View>
      </View>

      {/* approved-leave chip */}
      <View
        style={[
          {
            position: 'absolute',
            right: 16,
            top: 30,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 8,
            paddingVertical: 8,
            paddingLeft: 8,
            paddingRight: 13,
            borderRadius: radius.pill,
            backgroundColor: palette.surface,
            transform: [{ rotate: '5deg' }],
          },
          shadows.card,
        ]}
      >
        <View style={{ width: 24, height: 24, borderRadius: radius.pill, backgroundColor: tint.sage, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="check" size={14} color={palette.sage} stroke={2.4} />
        </View>
        <Text style={[font(700), { fontSize: 11.5, color: palette.ink }]}>Leave approved</Text>
      </View>

      {/* payslip row */}
      <View
        style={[
          {
            position: 'absolute',
            right: 16,
            bottom: 22,
            width: '60%',
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            padding: 11,
            borderRadius: radius.xl,
            backgroundColor: palette.surface,
            borderWidth: 1,
            borderColor: palette.line,
            transform: [{ rotate: '2deg' }],
          },
          shadows.card,
        ]}
      >
        <IconTile icon="wallet" color={palette.violet} background={tint.violet} size={36} iconSize={18} cornerRadius={11} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text numberOfLines={1} style={[font(700), { fontSize: 12.5, color: palette.ink }]}>Payslip ready</Text>
          <Text numberOfLines={1} style={[font(500), { fontSize: 10.5, lineHeight: 14, color: palette.faint, marginTop: 2 }]}>Net pay · EPF · SOCSO</Text>
        </View>
        <Icon name="chevR" size={15} color={palette.faint} />
      </View>
    </LinearGradient>
  );
}
