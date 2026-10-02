import React from 'react';
import { View, Text, type TextStyle } from 'react-native';
import { Button, LiveDot } from '@/components/ui';
import { useLiveTimer, pad2 } from '@/hooks';
import type { TodayStatus } from '@/api/types';
import { palette, font, radius, shadows, onDark, type } from '@/theme';

const NUM: TextStyle = { fontVariant: ['tabular-nums'] };

type Props = {
  today: TodayStatus;
  onClockIn: () => void;
  onClockOut: () => void;
  /** A clock-out request is in flight. */
  clockingOut?: boolean;
};

/** Format an ISO instant to "h:mm AM/PM". Returns null when missing/invalid. */
function formatTime(iso: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const h = d.getHours() % 12 || 12;
  return `${h}:${pad2(d.getMinutes())} ${d.getHours() >= 12 ? 'PM' : 'AM'}`;
}

/** Eyebrow row: status dot + label, with the clock-in time on the right when known. */
function HeroTop({ label, live, inAt }: { label: string; live?: boolean; inAt?: string | null }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {live ? (
          <LiveDot color={onDark.live} />
        ) : (
          <View style={{ width: 7, height: 7, borderRadius: radius.pill, backgroundColor: onDark.idle }} />
        )}
        <Text style={[type.eyebrow, { color: onDark.eyebrow }]}>{label}</Text>
      </View>
      {inAt ? <Text style={[font(500), { fontSize: 12, color: onDark.text }]}>In at {inAt}</Text> : null}
    </View>
  );
}

/** Running worked time since clock-in. Its own component so only it re-renders each second. */
function RunningTime({ since }: { since: string }) {
  const { h, m, s } = useLiveTimer(since);
  return (
    <Text style={[font(800), { fontSize: 44, lineHeight: 52, letterSpacing: -1.4, color: palette.white, marginTop: 9 }, NUM]}>
      {h}:{pad2(m)}
      <Text style={[font(800), { fontSize: 24, color: onDark.dim, letterSpacing: -0.5 }]}>:{pad2(s)}</Text>
    </Text>
  );
}

/**
 * Espresso attendance hero for staff Home. Three honest states, straight from
 * today's attendance record:
 *  - not clocked in  → calm prompt + "Clock In" (or "On leave today")
 *  - clocked in      → live worked time since clock-in + "Clock Out"
 *  - clocked out     → "Done for today" with the final worked total
 */
export function AttendanceHero({ today, onClockIn, onClockOut, clockingOut = false }: Props) {
  const clockedIn = today.clockInAt != null;
  const clockedOut = clockedIn && today.workedMinutes != null;
  const onLeave = !clockedIn && today.status.toUpperCase() === 'ON_LEAVE';
  const inAt = formatTime(today.clockInAt);
  const shift = today.shift ? today.shift : null;

  let body: React.ReactNode;
  if (!clockedIn) {
    body = (
      <>
        <HeroTop label="Today" />
        <Text style={[font(800), { fontSize: 28, lineHeight: 35, letterSpacing: -0.8, color: palette.white, marginTop: 12 }]}>
          {onLeave ? 'On leave today' : 'Not clocked in yet'}
        </Text>
        <Text style={[font(500), { fontSize: 12.5, lineHeight: 18, color: onDark.text, marginTop: 6 }]}>
          {onLeave
            ? 'Enjoy your time off. Working part of the day? You can still clock in.'
            : shift
              ? `Your shift · ${shift}`
              : 'Clock in when you start work.'}
        </Text>
        <Button
          label="Clock In"
          icon="clock"
          variant={onLeave ? 'light' : 'primary'}
          height={48}
          style={{ marginTop: 16 }}
          onPress={onClockIn}
        />
      </>
    );
  } else if (!clockedOut) {
    body = (
      <>
        <HeroTop label="Currently working" live inAt={inAt} />
        <RunningTime since={today.clockInAt as string} />
        <Text style={[font(500), { fontSize: 12.5, color: onDark.text, marginTop: 4 }]}>
          Worked today{shift ? ` · ${shift}` : ''}
        </Text>
        <Button
          label={clockingOut ? 'Clocking out…' : 'Clock Out'}
          icon="clock"
          variant="light"
          height={48}
          disabled={clockingOut}
          style={{ marginTop: 16 }}
          onPress={onClockOut}
        />
      </>
    );
  } else {
    const total = Math.max(0, today.workedMinutes ?? 0);
    body = (
      <>
        <HeroTop label="Done for today" inAt={inAt} />
        <Text style={[font(800), { fontSize: 44, lineHeight: 52, letterSpacing: -1.4, color: palette.white, marginTop: 9 }, NUM]}>
          {Math.floor(total / 60)}
          <Text style={[font(800), { fontSize: 24, color: onDark.dim, letterSpacing: -0.5 }]}>h </Text>
          {pad2(total % 60)}
          <Text style={[font(800), { fontSize: 24, color: onDark.dim, letterSpacing: -0.5 }]}>m</Text>
        </Text>
        <Text style={[font(500), { fontSize: 12.5, color: onDark.text, marginTop: 4 }]}>
          Worked today{shift ? ` · ${shift}` : ''}
        </Text>
      </>
    );
  }

  return (
    <View style={[{ backgroundColor: palette.espresso, borderRadius: radius.hero, padding: 20, paddingBottom: clockedOut ? 20 : 18, overflow: 'hidden' }, shadows.float]}>
      {/* single restrained coral wash, pushed off-canvas so only a soft arc reads */}
      <View style={{ position: 'absolute', right: -84, top: -104, width: 230, height: 230, borderRadius: radius.pill, backgroundColor: onDark.glow }} />
      {body}
    </View>
  );
}
