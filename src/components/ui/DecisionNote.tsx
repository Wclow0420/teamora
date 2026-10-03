import React from 'react';
import { Text } from 'react-native';
import { palette, font } from '@/theme';

type Props = {
  /** The approver's note (e.g. why a request was declined). Renders nothing when empty. */
  note?: string | null;
};

/** Small "Reason: …" line under a declined leave / claim / overtime row. */
export function DecisionNote({ note }: Props) {
  const text = note?.trim();
  if (!text) return null;
  return (
    <Text style={[font(500), { fontSize: 11.5, lineHeight: 16, color: palette.soft, marginTop: 5 }]}>
      <Text style={[font(700), { color: palette.danger }]}>Reason: </Text>
      {text}
    </Text>
  );
}
