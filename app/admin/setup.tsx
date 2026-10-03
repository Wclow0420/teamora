import React from 'react';
import { CompanySetupWizard } from '@/features/setup/CompanySetupWizard';

/**
 * Resume the guided company setup (opened from the dashboard's "Finish setting
 * up" card). Starts at the work week, prefilled from what's already saved.
 */
export default function AdminSetup() {
  return <CompanySetupWizard mode="resume" />;
}
