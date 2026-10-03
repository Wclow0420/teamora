import React from 'react';
import { LegalDocumentScreen } from '@/components/legal/LegalDocumentScreen';
import { TERMS_OF_USE } from '@/content/legal';

/** Terms of use — in the (auth) group so it's reachable signed in or out. Text: src/content/legal.ts. */
export default function Terms() {
  return <LegalDocumentScreen doc={TERMS_OF_USE} related={{ label: 'Read the Privacy notice', href: '/privacy' }} />;
}
