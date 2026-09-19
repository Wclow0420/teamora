import { useEffect, useState } from 'react';

export type TimerParts = { h: number; m: number; s: number };

/**
 * Counts up once per second from `baseSeconds`. Powers the live "currently
 * working" timer on the staff home screen. Returns split h/m/s parts.
 */
export function useLiveTimer(baseSeconds = 6 * 3600 + 24 * 60): TimerParts {
  const [seconds, setSeconds] = useState(baseSeconds);

  useEffect(() => {
    const id = setInterval(() => setSeconds((x) => x + 1), 1000);
    return () => clearInterval(id);
  }, []);

  return {
    h: Math.floor(seconds / 3600),
    m: Math.floor((seconds % 3600) / 60),
    s: seconds % 60,
  };
}

/** Zero-pads a number to two digits ("9" → "09"). */
export const pad2 = (n: number) => String(n).padStart(2, '0');
