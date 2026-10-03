/**
 * Readable temporary passwords for people an admin has just added, e.g.
 * "Kopi-4821-teh": easy to read out or paste into a chat, and always long
 * enough for the server's password rule. It is a *temporary* credential the
 * person is told to change — not a secret meant to last.
 */

const WORDS = [
  'kopi', 'teh', 'nasi', 'roti', 'satay', 'laksa', 'kaya', 'pandan', 'mango', 'durian',
  'sambal', 'cendol', 'rendang', 'ketupat', 'lemang', 'pisang', 'kelapa', 'bunga', 'pantai', 'bukit',
  'sungai', 'langit', 'bintang', 'bulan', 'awan', 'angin', 'hujan', 'pelangi', 'rumah', 'kampung',
] as const;

type CryptoLike = { getRandomValues?: (array: Uint32Array) => Uint32Array };

/** A random integer in [0, max). Uses the platform CSPRNG when the runtime has one. */
function randomInt(max: number): number {
  const crypto = (globalThis as { crypto?: CryptoLike }).crypto;
  if (crypto?.getRandomValues) {
    try {
      const buf = crypto.getRandomValues(new Uint32Array(1));
      return buf[0] % max;
    } catch {
      // fall through
    }
  }
  return Math.floor(Math.random() * max);
}

/** word-4digits-word, first letter capitalised — always ≥ 10 characters. */
export function generateTempPassword(): string {
  const first = WORDS[randomInt(WORDS.length)];
  let second = WORDS[randomInt(WORDS.length)];
  if (second === first) second = WORDS[(WORDS.indexOf(second) + 1) % WORDS.length];
  const digits = String(1000 + randomInt(9000));
  return `${first.charAt(0).toUpperCase()}${first.slice(1)}-${digits}-${second}`;
}
