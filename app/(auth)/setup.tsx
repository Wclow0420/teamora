import React, { useRef } from 'react';
import { Redirect } from 'expo-router';
import { CompanySetupWizard } from '@/features/setup/CompanySetupWizard';
import { useAuth } from '@/context/AuthContext';

/**
 * New-company setup, entered signed out (Onboarding "Get started", Login
 * "Create an account"). The wizard signs the owner up at its third step and
 * keeps going — see `useCompanySetupSteps`.
 */
export default function Setup() {
  const { status } = useAuth();
  // Someone who was already signed in when this opened has no business creating
  // a second company from here — send them through the normal role redirect.
  // (Captured once: the wizard itself signs in midway, which must not redirect.)
  const arrivedSignedIn = useRef(status === 'authenticated').current;
  if (arrivedSignedIn) return <Redirect href="/" />;
  return <CompanySetupWizard mode="new" />;
}
