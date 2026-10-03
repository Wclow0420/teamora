import React from 'react';
import { Text, View } from 'react-native';
import { Button, Card, IconTile, type IconName } from '@/components/ui';
import { palette, radius, tint, type } from '@/theme';

/** What's still ahead — the same steps, in the same order, as the wizard. */
const AHEAD: { icon: IconName; label: string; color: string; bg: string }[] = [
  { icon: 'calendar', label: 'Work week', color: palette.coral, bg: tint.coral },
  { icon: 'sun', label: 'Leave', color: palette.amber, bg: tint.amber },
  { icon: 'gift', label: 'Holidays', color: palette.sage, bg: tint.sage },
  { icon: 'pin', label: 'Workplace', color: palette.violet, bg: tint.violet },
  { icon: 'users', label: 'Team', color: palette.sage, bg: tint.sage },
];

type Props = { onPress: () => void };

/**
 * Dashboard prompt for an owner whose company hasn't finished the guided setup.
 * Shows what the setup covers (not a progress figure — the server only knows
 * finished / not finished) and one clear action.
 */
export function FinishSetupCard({ onPress }: Props) {
  return (
    <Card padding={18} style={{ borderRadius: radius['3xl'], marginBottom: 14 }}>
      <Text style={[type.eyebrow, { color: palette.coral }]}>Almost there</Text>
      <Text style={[type.h2, { color: palette.ink, marginTop: 8 }]}>Finish setting up your company</Text>
      <Text style={[type.body, { color: palette.soft, marginTop: 6 }]}>
        A few quick choices so attendance, leave and payroll work the way your company does. About 3 minutes.
      </Text>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 16 }}>
        {AHEAD.map((s) => (
          <View key={s.label} style={{ alignItems: 'center', gap: 6, flex: 1 }}>
            <IconTile icon={s.icon} color={s.color} background={s.bg} size={38} iconSize={19} cornerRadius={radius.md} />
            <Text style={[type.micro, { color: palette.faint }]} numberOfLines={1}>
              {s.label}
            </Text>
          </View>
        ))}
      </View>
      <View style={{ marginTop: 18 }}>
        <Button label="Continue setup" icon="arrowR" iconTrailing onPress={onPress} />
      </View>
    </Card>
  );
}
