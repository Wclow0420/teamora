import React from 'react';
import { Text, View } from 'react-native';
import Animated, { FadeIn, FadeOut, LinearTransition, useReducedMotion } from 'react-native-reanimated';
import { Button, Card, ChoiceTile, IconTile, Stepper, TextField } from '@/components/ui';
import { palette, radius, tint, type } from '@/theme';
import type { WorkplaceErrors, WorkplaceForm } from './types';

export const RADIUS_MIN = 100;
export const RADIUS_MAX = 1000;
export const RADIUS_STEP = 50;

type Props = {
  form: WorkplaceForm;
  errors: WorkplaceErrors;
  onChange: (patch: Partial<WorkplaceForm>) => void;
  locating: boolean;
  onUseCurrentLocation: () => void;
  /** Names of sites the company already has (resuming setup). */
  existingSites: string[];
};

/** "Where does your team clock in?" — one site with a GPS check, or anywhere. */
export function WorkplaceStep({ form, errors, onChange, locating, onUseCurrentLocation, existingSites }: Props) {
  const reduce = useReducedMotion();
  const { coords } = form;
  return (
    <View style={{ gap: 12 }}>
      {existingSites.length > 0 && (
        <Text style={[type.meta, { fontSize: 12.5, lineHeight: 18, color: palette.soft, marginBottom: 2 }]}>
          Already set up: {existingSites.join(', ')}. Add another below, or skip.
        </Text>
      )}
      <ChoiceTile
        icon="building"
        title="One office or site"
        subtitle="Staff clock in only when they're at the workplace."
        selected={form.choice === 'site'}
        onPress={() => onChange({ choice: 'site' })}
      />
      <ChoiceTile
        icon="pin"
        title="Anywhere"
        subtitle="No location check — good for remote or field teams."
        selected={form.choice === 'anywhere'}
        onPress={() => onChange({ choice: 'anywhere' })}
      />

      {form.choice === 'site' && (
        <Animated.View
          entering={reduce ? undefined : FadeIn.duration(240)}
          exiting={reduce ? undefined : FadeOut.duration(140)}
          layout={reduce ? undefined : LinearTransition.duration(200)}
          style={{ gap: 14, marginTop: 10 }}
        >
          <TextField
            label="Site name"
            value={form.name}
            onChangeText={(name) => onChange({ name })}
            placeholder="e.g. KL Head Office"
            autoCapitalize="words"
            maxLength={120}
            error={errors.name}
          />

          <View>
            <Card
              padding={14}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 13,
                borderRadius: radius.lg,
                borderColor: errors.coords ? palette.danger : palette.line,
              }}
            >
              <IconTile
                icon="pin"
                color={coords ? palette.sage : palette.violet}
                background={coords ? tint.sage : tint.violet}
                size={40}
                iconSize={20}
                cornerRadius={radius.md}
              />
              <View style={{ flex: 1, minWidth: 0 }}>
                {coords ? (
                  <>
                    <Text style={[type.bodyStrong, { color: palette.ink }]}>Location captured</Text>
                    <Text style={[type.meta, { color: palette.faint, marginTop: 3, fontVariant: ['tabular-nums'] }]} numberOfLines={1}>
                      {coords.latitude.toFixed(5)}, {coords.longitude.toFixed(5)}
                    </Text>
                  </>
                ) : (
                  <Text style={[type.meta, { fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>
                    Stand at the workplace, then capture its GPS pin.
                  </Text>
                )}
              </View>
            </Card>
            {!!errors.coords && (
              <Text style={[type.meta, { fontSize: 11.5, lineHeight: 16, color: palette.danger, marginTop: 6 }]}>{errors.coords}</Text>
            )}
            <Button
              label={coords ? 'Update to my current location' : 'Use my current location'}
              icon="pin"
              variant="light"
              loading={locating}
              onPress={onUseCurrentLocation}
              style={{ marginTop: 10 }}
            />
          </View>

          <View>
            <Stepper
              label="Clock-in radius"
              value={form.radiusM}
              onChange={(radiusM) => onChange({ radiusM })}
              min={RADIUS_MIN}
              max={RADIUS_MAX}
              step={RADIUS_STEP}
              format={(n) => `${n} m`}
            />
            <Text style={[type.meta, { lineHeight: 18, color: palette.faint, marginTop: 8 }]}>
              Staff must be within {form.radiusM} m to clock in. People you invite next are assigned to this site.
            </Text>
          </View>
        </Animated.View>
      )}
    </View>
  );
}
