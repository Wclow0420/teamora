import React from 'react';
import { Text, View } from 'react-native';
import { PointList, type Point } from '@/components/ui';
import { palette, tint, type } from '@/theme';

const AHEAD: Point[] = [
  { icon: 'building', text: 'Create your company account', tone: { color: palette.coral, bg: tint.coral } },
  { icon: 'calendar', text: 'Set your work week and leave', tone: { color: palette.amber, bg: tint.amber } },
  { icon: 'gift', text: 'Add public holidays', tone: { color: palette.sage, bg: tint.sage } },
  { icon: 'users', text: 'Choose where staff clock in, and invite your team', tone: { color: palette.violet, bg: tint.violet } },
];

type Props = { onSignIn: () => void };

/** Welcome body: what the next few minutes cover, and a way out for existing users. */
export function WelcomeStep({ onSignIn }: Props) {
  return (
    <View>
      <PointList points={AHEAD} />
      <Text style={[type.body, { color: palette.soft, textAlign: 'center', marginTop: 22 }]}>
        Already use Teamora?{' '}
        <Text accessibilityRole="link" onPress={onSignIn} style={[type.bodyStrong, { color: palette.coral }]}>
          Sign in
        </Text>
      </Text>
    </View>
  );
}
