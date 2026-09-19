import React from 'react';
import { View, Text, ActivityIndicator, Pressable } from 'react-native';
import { palette, font, radius } from '@/theme';

type Props = {
  loading: boolean;
  error?: unknown;
  onRetry?: () => void;
  /** Render only when not loading and no error. */
  children: React.ReactNode;
  /** Min height while loading/erroring so layout doesn't jump. */
  minHeight?: number;
};

/**
 * Small inline loading/error gate for data-backed sections. Keeps screens tidy:
 *   <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>…</AsyncBoundary>
 */
export function AsyncBoundary({ loading, error, onRetry, children, minHeight = 160 }: Props) {
  if (loading) {
    return (
      <View style={{ minHeight, alignItems: 'center', justifyContent: 'center', paddingVertical: 24 }}>
        <ActivityIndicator color={palette.coral} />
      </View>
    );
  }
  if (error) {
    return (
      <View style={{ minHeight, alignItems: 'center', justifyContent: 'center', paddingVertical: 24, gap: 12 }}>
        <Text style={[font(600), { fontSize: 13, color: palette.soft, textAlign: 'center' }]}>
          Couldn't load this. Check your connection.
        </Text>
        {onRetry && (
          <Pressable
            onPress={onRetry}
            style={{ paddingVertical: 9, paddingHorizontal: 16, borderRadius: radius.pill, backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.line }}
          >
            <Text style={[font(700), { fontSize: 12.5, color: palette.coral }]}>Try again</Text>
          </Pressable>
        )}
      </View>
    );
  }
  return <>{children}</>;
}
