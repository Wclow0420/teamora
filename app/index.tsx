import React from 'react';
import { View, ActivityIndicator } from 'react-native';
import { Redirect } from 'expo-router';
import { EmptyState } from '@/components/ui';
import { useAuth } from '@/context/AuthContext';
import { isAdminRole } from '@/api/types';
import { palette, spacing, tint } from '@/theme';

/**
 * Entry gate. While the persisted session is being restored we show a splash
 * spinner, then redirect by role. If the server can't be reached we keep the
 * session and offer a retry — being offline never signs anyone out.
 */
export default function Index() {
  const { status, role, retryRestore } = useAuth();

  if (status === 'loading') {
    return (
      <View style={{ flex: 1, backgroundColor: palette.bg, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={palette.coral} />
      </View>
    );
  }

  if (status === 'offline') {
    return (
      <View style={{ flex: 1, backgroundColor: palette.bg, justifyContent: 'center', paddingHorizontal: spacing.screenX }}>
        <EmptyState
          icon="shield"
          title="Can't reach Teamora"
          subtitle="You're still signed in. Check your internet connection, then try again."
          tone={{ color: palette.coral, bg: tint.coral }}
          action={{ label: 'Try again', onPress: () => void retryRestore() }}
        />
      </View>
    );
  }

  if (status === 'authenticated') {
    return <Redirect href={isAdminRole(role) ? '/admin/dashboard' : '/home'} />;
  }
  return <Redirect href="/onboarding" />;
}
