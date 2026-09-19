import React from 'react';
import { View, Text } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Icon, IconTile, Placeholder } from '@/components/ui';
import { palette, font, radius, gradients, tint } from '@/theme';

const FEATURES = [
  { icon: 'face', title: 'Clock in with a glance', desc: 'Face + GPS check in under 2 seconds', color: palette.coral, bg: tint.coral },
  { icon: 'sun', title: 'Time off in a tap', desc: 'Apply for leave and track approvals live', color: palette.amber, bg: tint.amber },
  { icon: 'wallet', title: 'Payday, crystal clear', desc: 'See every ringgit before it lands', color: palette.sage, bg: tint.sage },
] as const;

export default function Onboarding() {
  const router = useRouter();
  return (
    <Screen paddingX={24}>
      {/* brand row */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 9 }}>
          <LinearGradient colors={gradients.avatar} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ width: 30, height: 30, borderRadius: 9 }} />
          <Text style={[font(800), { fontSize: 18, color: palette.ink, letterSpacing: -0.5 }]}>lumi</Text>
        </View>
        <Text style={[font(600), { fontSize: 13, color: palette.faint }]} onPress={() => router.push('/login')}>
          Skip
        </Text>
      </View>

      {/* hero */}
      <LinearGradient colors={gradients.creamSoft} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={{ marginTop: 18, height: 224, borderRadius: 26, borderWidth: 1, borderColor: '#F1D2B4', padding: 14 }}>
        <Placeholder label="onboarding illustration" radius={16} tintColor="rgba(236,106,77,0.12)" />
      </LinearGradient>

      <View style={{ marginTop: 22 }}>
        <Text style={[font(800), { fontSize: 27, lineHeight: 32, color: palette.ink, letterSpacing: -0.8 }]}>
          Your whole workday,{'\n'}in one warm place.
        </Text>
        <Text style={[font(500), { fontSize: 13.5, lineHeight: 20, color: palette.soft, marginTop: 10 }]}>
          Attendance, leave, claims and payslips — friendly enough for day one, powerful enough for HR.
        </Text>
      </View>

      {/* features */}
      <View style={{ marginTop: 18, gap: 11 }}>
        {FEATURES.map((f) => (
          <View key={f.title} style={{ flexDirection: 'row', alignItems: 'center', gap: 13 }}>
            <IconTile icon={f.icon} color={f.color} background={f.bg} size={42} cornerRadius={radius.md} />
            <View style={{ flex: 1 }}>
              <Text style={[font(700), { fontSize: 14, color: palette.ink }]}>{f.title}</Text>
              <Text style={[font(500), { fontSize: 12, lineHeight: 16, color: palette.faint, marginTop: 4 }]}>{f.desc}</Text>
            </View>
          </View>
        ))}
      </View>

      <View style={{ marginTop: 28 }}>
        <Button label="Get started" icon="arrowR" iconTrailing height={54} onPress={() => router.push('/login')} />
        <Text
          style={[font(600), { fontSize: 13, color: palette.soft, textAlign: 'center', marginTop: 14 }]}
          onPress={() => router.push('/login')}
        >
          I already have an account
        </Text>
      </View>
    </Screen>
  );
}
