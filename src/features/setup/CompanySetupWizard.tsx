import React from 'react';
import { Alert } from 'react-native';
import { useRouter } from 'expo-router';
import { Wizard } from '@/components/wizard';
import { registerPushToken } from '@/notifications/push';
import { useCompanySetupSteps } from './useCompanySetupSteps';
import type { SetupMode } from './types';

type Props = { mode: SetupMode };

/**
 * The guided company setup, rendered by both routes:
 *  - `/setup` (`mode="new"`, signed out): welcome → you → company → … → done.
 *  - `/admin/setup` (`mode="resume"`, signed-in owner): work week → … → done.
 * Every step saves as it goes, so leaving early is safe — the dashboard's
 * "Finish setting up" card brings the owner back.
 */
export function CompanySetupWizard({ mode }: Props) {
  const router = useRouter();
  const { steps, accountCreated } = useCompanySetupSteps(mode);

  /** Into the admin app. New accounts also get their (deferred) push-permission prompt here. */
  const toDashboard = () => {
    if (mode === 'new') {
      void registerPushToken();
      router.replace('/admin/dashboard');
    } else if (router.canGoBack()) {
      router.back();
    } else {
      router.replace('/admin/dashboard');
    }
  };

  const onExit = () => {
    // Nothing created yet → just leave.
    if (mode === 'new' && !accountCreated) {
      if (router.canGoBack()) router.back();
      else router.replace('/onboarding');
      return;
    }
    if (mode === 'resume') {
      toDashboard();
      return;
    }
    // The company exists but setup isn't finished — leaving is fine, say how to return.
    Alert.alert('Finish setting up later?', "What you've done so far is saved. You can pick this up again from your dashboard.", [
      { text: 'Keep going', style: 'cancel' },
      { text: 'Finish later', onPress: toDashboard },
    ]);
  };

  return <Wizard steps={steps} onFinish={toDashboard} onExit={onExit} />;
}
