import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

export type TimerParts = { h: number; m: number; s: number };

/** Whole seconds between an ISO instant and now — never negative, 0 if unparseable. */
function elapsedSeconds(startIso: string | null | undefined, deductMinutes: number): number {
  if (!startIso) return 0;
  const start = new Date(startIso).getTime();
  if (Number.isNaN(start)) return 0;
  const deduct = Number.isFinite(deductMinutes) && deductMinutes > 0 ? deductMinutes * 60 : 0;
  return Math.max(0, Math.floor((Date.now() - start) / 1000) - deduct);
}

/**
 * Live elapsed time since `startIso` (the clock-in instant), split into h/m/s.
 *
 * The value is always derived from the wall clock (`now − start`), never from a
 * counter — so it is right the moment the screen opens, and stays right after
 * the app has been backgrounded (timers are paused there; we also re-sync as
 * soon as the app returns to the foreground). A null/invalid start yields 0:00:00.
 *
 * `deductMinutes` is time that doesn't count as worked — the break between
 * clocking out and clocking in again the same day — so the result is
 * `now − start − break`, never negative.
 */
export function useLiveTimer(startIso: string | null | undefined, deductMinutes: number = 0): TimerParts {
  const [seconds, setSeconds] = useState(() => elapsedSeconds(startIso, deductMinutes));

  useEffect(() => {
    const sync = () => setSeconds(elapsedSeconds(startIso, deductMinutes));
    sync();
    if (!startIso) return;
    const id = setInterval(sync, 1000);
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') sync();
    });
    return () => {
      clearInterval(id);
      sub.remove();
    };
  }, [startIso, deductMinutes]);

  return {
    h: Math.floor(seconds / 3600),
    m: Math.floor((seconds % 3600) / 60),
    s: seconds % 60,
  };
}

/** Zero-pads a number to two digits ("9" → "09"). */
export const pad2 = (n: number) => String(n).padStart(2, '0');
