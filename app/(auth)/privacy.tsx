import React from 'react';
import { LegalDocumentScreen } from '@/components/legal/LegalDocumentScreen';
import { PRIVACY_NOTICE } from '@/content/legal';

/** Privacy notice — in the (auth) group so it's reachable signed in or out. Text: src/content/legal.ts. */
export default function Privacy() {
  return <LegalDocumentScreen doc={PRIVACY_NOTICE} related={{ label: 'Read the Terms of use', href: '/terms' }} />;
}
