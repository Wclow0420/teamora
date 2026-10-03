import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { useRouter, type Href } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Chip, Icon, ScreenHeader } from '@/components/ui';
import {
  LEGAL_DRAFT,
  LEGAL_DRAFT_NOTE,
  LEGAL_LAST_UPDATED,
  type LegalBullet,
  type LegalDocument,
} from '@/content/legal';
import { palette, font, radius, spacing, tint, type } from '@/theme';

type Props = {
  doc: LegalDocument;
  /** The companion document (Privacy ↔ Terms), linked at the foot of the page. */
  related: { label: string; href: Href };
};

/**
 * Renders a {@link LegalDocument} from `src/content/legal.ts` as plain, readable
 * text. Lives in the (auth) group so it's reachable signed in or out.
 */
export function LegalDocumentScreen({ doc, related }: Props) {
  const router = useRouter();

  return (
    <Screen>
      <ScreenHeader back title={doc.title} subtitle={`Last updated ${LEGAL_LAST_UPDATED}`} />

      {LEGAL_DRAFT && (
        <Chip
          label={LEGAL_DRAFT_NOTE}
          color={palette.amber}
          background={tint.amber}
          leading={<Icon name="doc" size={13} color={palette.amber} />}
          style={{ marginBottom: spacing.md }}
        />
      )}

      <View style={{ gap: 12 }}>
        {doc.intro.map((p) => (
          <Text key={p} style={[type.body, { color: palette.ink }]}>
            {p}
          </Text>
        ))}
      </View>

      {doc.sections.map((section) => (
        <View key={section.heading} style={{ marginTop: 26 }}>
          <Text accessibilityRole="header" style={[type.h3, { color: palette.ink, marginBottom: 10 }]}>
            {section.heading}
          </Text>
          <View style={{ gap: 10 }}>
            {section.body?.map((p) => <Paragraph key={p} text={p} />)}
            {section.bullets?.map((b, i) => <Bullet key={i} bullet={b} />)}
            {section.after?.map((p) => <Paragraph key={p} text={p} />)}
          </View>
        </View>
      ))}

      <Pressable
        onPress={() => router.push(related.href)}
        accessibilityRole="link"
        style={({ pressed }) => ({
          marginTop: 30,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 10,
          paddingVertical: 14,
          paddingHorizontal: 16,
          borderRadius: radius.lg,
          borderWidth: 1,
          borderColor: palette.line,
          backgroundColor: pressed ? palette.surfaceSunken : palette.surface,
        })}
      >
        <Icon name="doc" size={18} color={palette.coral} />
        <Text style={[type.bodyStrong, { flex: 1, color: palette.ink }]}>{related.label}</Text>
        <Icon name="chevR" size={16} color={palette.faint} />
      </Pressable>
    </Screen>
  );
}

function Paragraph({ text }: { text: string }) {
  return <Text style={[type.body, { color: palette.soft }]}>{text}</Text>;
}

function Bullet({ bullet }: { bullet: LegalBullet }) {
  return (
    <View style={{ flexDirection: 'row', gap: 10 }}>
      <View style={{ width: 5, height: 5, borderRadius: 3, backgroundColor: palette.coral, marginTop: 8.5 }} />
      <Text style={[type.body, { flex: 1, color: palette.soft }]}>
        {typeof bullet === 'string' ? (
          bullet
        ) : (
          <>
            <Text style={[font(700), { color: palette.ink }]}>{bullet.term}</Text>
            {` — ${bullet.text}`}
          </>
        )}
      </Text>
    </View>
  );
}
