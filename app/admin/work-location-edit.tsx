import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, Card, Icon, IconTile, SelectChips, TextField, type SelectOption } from '@/components/ui';
import { useCreateWorkLocation, useUpdateWorkLocation } from '@/api/queries';
import { ApiError } from '@/api/client';
import { getCurrentCoords, LocationError } from '@/lib/location';
import { palette, font, radius, tint } from '@/theme';

const MIN_RADIUS_M = 50;
const DEFAULT_RADIUS_M = 100;

const ACTIVE_OPTIONS: SelectOption<'ACTIVE' | 'INACTIVE'>[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];

/** Parse a radius input → an integer metre value, or undefined if invalid. */
function parseRadius(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isInteger(n) ? n : undefined;
}

/** Parse a coordinate param → a finite number, or null. */
function parseCoord(v: string | undefined): number | null {
  if (v == null || v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export default function WorkLocationEdit() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    id?: string;
    name?: string;
    latitude?: string;
    longitude?: string;
    radiusM?: string;
    active?: string;
  }>();
  const isEdit = !!params.id;

  const create = useCreateWorkLocation();
  const update = useUpdateWorkLocation();

  const [name, setName] = useState(params.name ?? '');
  const [latitude, setLatitude] = useState<number | null>(parseCoord(params.latitude));
  const [longitude, setLongitude] = useState<number | null>(parseCoord(params.longitude));
  const [radiusText, setRadiusText] = useState(params.radiusM ?? String(DEFAULT_RADIUS_M));
  const [active, setActive] = useState<'ACTIVE' | 'INACTIVE'>(params.active === 'false' ? 'INACTIVE' : 'ACTIVE');
  const [error, setError] = useState<string | null>(null);

  const [locating, setLocating] = useState(false);
  const hasCoords = latitude != null && longitude != null;
  const busy = create.isPending || update.isPending;

  const onUseCurrentLocation = async () => {
    setError(null);
    setLocating(true);
    try {
      const coords = await getCurrentCoords();
      setLatitude(coords.latitude);
      setLongitude(coords.longitude);
    } catch (e) {
      if (e instanceof LocationError && e.kind === 'denied') {
        setError('Location permission denied. Enable location for Teamora in Settings, then try again.');
      } else {
        setError('Could not get your current location. Move to an open area and try again.');
      }
    } finally {
      setLocating(false);
    }
  };

  const onSubmit = async () => {
    setError(null);
    if (!name.trim()) {
      setError('Give the work location a name.');
      return;
    }
    if (latitude == null || longitude == null) {
      setError('Capture the site coordinates with "Use my current location".');
      return;
    }
    const radiusM = parseRadius(radiusText);
    if (radiusM === undefined || radiusM < MIN_RADIUS_M) {
      setError(`Radius must be at least ${MIN_RADIUS_M} m.`);
      return;
    }
    try {
      if (isEdit && params.id) {
        await update.mutateAsync({
          id: params.id,
          body: { name: name.trim(), latitude, longitude, radiusM, active: active === 'ACTIVE' },
        });
      } else {
        await create.mutateAsync({
          name: name.trim(),
          latitude,
          longitude,
          radiusM,
          active: active === 'ACTIVE',
        });
      }
      router.back();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title={isEdit ? 'Edit work location' : 'New work location'} />

      <View style={{ gap: 14, marginTop: 8 }}>
        <TextField label="Name" value={name} onChangeText={setName} placeholder="e.g. KL Head Office" />

        {/* coordinate capture */}
        <View>
          <Text style={[font(700), { fontSize: 12, color: palette.soft, marginBottom: 8 }]}>Site coordinates</Text>
          <Card padding={14} style={{ flexDirection: 'row', alignItems: 'center', gap: 13, borderRadius: radius.lg }}>
            <IconTile icon="pin" color={palette.violet} background={tint.violet} size={40} iconSize={20} cornerRadius={12} />
            <View style={{ flex: 1, minWidth: 0 }}>
              {hasCoords ? (
                <>
                  <Text style={[font(700), { fontSize: 13.5, color: palette.ink, fontVariant: ['tabular-nums'] }]} numberOfLines={1}>
                    {latitude.toFixed(6)}, {longitude.toFixed(6)}
                  </Text>
                  <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]}>Captured pin</Text>
                </>
              ) : (
                <Text style={[font(500), { fontSize: 12.5, color: palette.faint }]} numberOfLines={2}>
                  Stand at the site and capture its GPS pin.
                </Text>
              )}
            </View>
          </Card>
          <Button
            label={locating ? 'Getting location…' : hasCoords ? 'Update to current location' : 'Use my current location'}
            icon="pin"
            variant="light"
            disabled={locating}
            onPress={onUseCurrentLocation}
            style={{ marginTop: 10 }}
          />
        </View>

        <TextField
          label="Radius (metres)"
          value={radiusText}
          onChangeText={setRadiusText}
          keyboardType="number-pad"
          placeholder={String(DEFAULT_RADIUS_M)}
        />
        <View style={{ flexDirection: 'row', gap: 7, marginTop: -8 }}>
          <Icon name="shield" size={14} color={palette.faint} />
          <Text style={[font(500), { flex: 1, fontSize: 11.5, color: palette.faint, lineHeight: 16 }]}>
            GPS is only accurate to about 20–50 m, so keep the radius at {MIN_RADIUS_M} m or
            more (default {DEFAULT_RADIUS_M} m) to avoid locking staff out.
          </Text>
        </View>

        <SelectChips label="Status" options={ACTIVE_OPTIONS} value={active} onChange={setActive} />

        {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

        <Button
          label={busy ? 'Saving…' : isEdit ? 'Save changes' : 'Create work location'}
          onPress={onSubmit}
          disabled={busy}
          style={{ marginTop: 4 }}
        />
      </View>
    </Screen>
  );
}
