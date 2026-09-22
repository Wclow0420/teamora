import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, Card, Chip, EmptyState, Icon, IconTile } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useAdminWorkLocations } from '@/api/queries';
import type { WorkLocation } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

export default function WorkLocations() {
  const router = useRouter();
  const q = useAdminWorkLocations();

  const openEdit = (w: WorkLocation) =>
    router.push({
      pathname: '/admin/work-location-edit',
      params: {
        id: w.id,
        name: w.name,
        latitude: String(w.latitude),
        longitude: String(w.longitude),
        radiusM: String(w.radiusM),
        active: String(w.active),
      },
    });

  return (
    <Screen>
      <ScreenHeader back title="Work locations" />

      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4, marginBottom: 14, lineHeight: 16 }]}>
        Define your work sites. Assign one to each employee, and clock-in will
        require them to be within the site's radius.
      </Text>

      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data &&
          (q.data.length === 0 ? (
            <EmptyState
              icon="pin"
              title="No work locations yet"
              subtitle="Add your first site to enable geofenced clock-in."
              tone={{ color: palette.violet, bg: tint.violet }}
              action={{ label: 'Add work location', icon: 'plus', onPress: () => router.push('/admin/work-location-edit') }}
            />
          ) : (
            <View style={{ gap: 10 }}>
              <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
                {q.data.map((w, i) => (
                  <Pressable
                    key={w.id}
                    onPress={() => openEdit(w)}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 13,
                      paddingVertical: 13,
                      paddingHorizontal: 15,
                      borderTopWidth: i ? 1 : 0,
                      borderTopColor: palette.line,
                      opacity: w.active ? 1 : 0.55,
                    }}
                  >
                    <IconTile icon="pin" color={palette.violet} background={tint.violet} size={38} iconSize={19} cornerRadius={12} />
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                        {w.name}
                      </Text>
                      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]}>
                        {w.radiusM} m radius{w.active ? '' : ' · inactive'}
                      </Text>
                    </View>
                    <Chip
                      label={w.active ? 'Active' : 'Inactive'}
                      size="sm"
                      color={w.active ? palette.sage : palette.soft}
                      background={w.active ? tint.sage : tint.neutral}
                    />
                    <Icon name="chevR" size={16} color={palette.faint} />
                  </Pressable>
                ))}
              </Card>

              <Button
                label="Add work location"
                icon="plus"
                variant="light"
                onPress={() => router.push('/admin/work-location-edit')}
              />
            </View>
          ))}
      </AsyncBoundary>
    </Screen>
  );
}
