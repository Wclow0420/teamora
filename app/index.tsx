import React from 'react';
import { View, ActivityIndicator } from 'react-native';
import { Redirect } from 'expo-router';
import { useAuth } from '@/context/AuthContext';
import { isAdminRole } from '@/api/types';
import { palette } from '@/theme';

/**
 * Entry gate. While the persisted session is being restored we show a splash
 * spinner, then redirect by role.
 */
export default function Index() {
  const { status, role } = useAuth();

  if (status === 'loading') {
    return (
      <View style={{ flex: 1, backgroundColor: palette.bg, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={palette.coral} />
      </View>
    );
  }

  if (status === 'authenticated') {
    return <Redirect href={isAdminRole(role) ? '/admin/dashboard' : '/home'} />;
  }
  return <Redirect href="/onboarding" />;
}
