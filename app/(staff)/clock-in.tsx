import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Chip, Icon, IconTile, LiveDot } from '@/components/ui';
import { useClockIn, useMe } from '@/api/queries';
import { ApiError } from '@/api/client';
import { pad2 } from '@/hooks';
import { palette, font, radius, gradients } from '@/theme';

const DAYS_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** Current wall-clock time as a 12-hour "h:mm" + "AM"/"PM" pair. */
function clockParts(d: Date): { hm: string; ampm: string } {
  let h = d.getHours();
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12;
  if (h === 0) h = 12;
  return { hm: `${h}:${pad2(d.getMinutes())}`, ampm };
}

export default function ClockIn() {
  const router = useRouter();
  const clockIn = useClockIn();
  const me = useMe();
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null);

  // Live clock — refreshes the displayed time without a heavy per-second re-render.
  const [now, setNow] = React.useState(() => new Date());
  React.useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 10_000);
    return () => clearInterval(t);
  }, []);
  const { hm, ampm } = clockParts(now);
  const dateLong = `${DAYS_LONG[now.getDay()]}, ${now.getDate()} ${MONTHS_LONG[now.getMonth()]}`;
  const workLocation = me.data?.location ?? me.data?.companyName ?? 'Your workplace';

  const handleClockIn = async () => {
    setErrorMsg(null);
    try {
      await clockIn.mutateAsync();
      if (router.canGoBack()) router.back();
    } catch (e) {
      setErrorMsg(e instanceof ApiError ? e.message : 'Could not clock in. Please try again.');
    }
  };

  return (
    <Screen scroll={false} background="#1E1813" barStyle="light">
      {/* dark backdrop gradient behind content */}
      <LinearGradient
        colors={gradients.clockBackdrop}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}
      />

      <View style={{ flex: 1 }}>
        {/* top row */}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Pressable
            onPress={() => (router.canGoBack() ? router.back() : null)}
            style={{
              width: 40,
              height: 40,
              borderRadius: radius.md,
              backgroundColor: 'rgba(255,255,255,0.1)',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon name="chevL" size={18} color={palette.white} />
          </Pressable>
          <Text style={[font(600), { fontSize: 14, color: palette.white }]}>Clock In</Text>
          <View style={{ width: 38 }} />
        </View>

        {/* time */}
        <View style={{ alignItems: 'center', marginTop: 14 }}>
          <Text style={[font(700), { fontSize: 34, color: palette.white, letterSpacing: -0.7 }]}>
            {hm}
            <Text style={{ fontSize: 16, opacity: 0.7 }}> {ampm}</Text>
          </Text>
          <Text style={[font(500), { fontSize: 13, color: palette.white, opacity: 0.6, marginTop: 6 }]}>
            {dateLong}
          </Text>
        </View>

        {/* face viewfinder */}
        <View style={{ width: 232, height: 232, alignSelf: 'center', marginTop: 26 }}>
          <View
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              borderRadius: 999,
              borderWidth: 2,
              borderStyle: 'dashed',
              borderColor: 'rgba(236,106,77,0.53)',
            }}
          />
          <View
            style={{
              position: 'absolute',
              top: 14,
              left: 14,
              right: 14,
              bottom: 14,
              borderRadius: 999,
              overflow: 'hidden',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <LinearGradient
              colors={['#5a463a', '#2a211b']}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}
            />
            <Icon name="face" size={92} color="rgba(255,255,255,0.2)" stroke={1.2} />
          </View>
        </View>

        {/* scanning chip */}
        <View style={{ alignItems: 'center', marginTop: 22 }}>
          <Chip
            label="Scanning your face…"
            background="rgba(255,255,255,0.12)"
            color={palette.white}
            leading={<LiveDot color={palette.amber} />}
          />
        </View>

        {/* flexible spacer to push location card to bottom */}
        <View style={{ flex: 1 }} />

        {/* location card */}
        <View
          style={{
            backgroundColor: 'rgba(255,255,255,0.07)',
            borderRadius: radius['2xl'],
            padding: 16,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 13,
          }}
        >
          <IconTile icon="pin" color="#7BC79A" background="rgba(92,144,112,0.13)" size={44} iconSize={22} cornerRadius={14} />
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={[font(700), { fontSize: 14, color: palette.white }]} numberOfLines={1}>{workLocation}</Text>
            <Text style={[font(500), { fontSize: 12, color: palette.white, opacity: 0.6, marginTop: 5 }]}>
              Your work location
            </Text>
          </View>
        </View>

        {/* CTA */}
        {errorMsg && (
          <Text style={[font(600), { fontSize: 12.5, color: palette.amber, textAlign: 'center', marginTop: 12 }]}>
            {errorMsg}
          </Text>
        )}
        <Button
          label={clockIn.isPending ? 'Verifying…' : 'Verify & Clock In'}
          icon="fingerprint"
          height={56}
          style={{ marginTop: 14 }}
          onPress={handleClockIn}
          disabled={clockIn.isPending}
        />
      </View>
    </Screen>
  );
}
