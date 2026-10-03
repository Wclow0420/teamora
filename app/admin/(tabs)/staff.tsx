import React, { useState } from 'react';
import { View, Text, Pressable, TextInput } from 'react-native';
import { useRouter } from 'expo-router';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Card, Chip, EmptyState, Icon } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { useStaff } from '@/api/queries';
import { palette, font, radius, gradients, shadows, tint } from '@/theme';
import { LinearGradient } from 'expo-linear-gradient';

export default function Staff() {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const trimmed = query.trim();
  const q = useStaff(undefined, trimmed || undefined);
  const count = q.data?.length ?? 0;
  // Deactivated people stay listed (muted, "Inactive" chip, at the end) — this
  // list is the only way back to their record to reactivate them or look up
  // their history. The headcount counts active staff only.
  const people = q.data ? [...q.data].sort((a, b) => Number(!a.active) - Number(!b.active)) : [];
  const activeCount = q.data?.filter((e) => e.active).length ?? 0;
  const inactiveCount = count - activeCount;
  const headcount = `${activeCount} ${activeCount === 1 ? 'employee' : 'employees'}${
    inactiveCount > 0 ? ` · ${inactiveCount} inactive` : ''
  }`;
  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      large
      title="Staff"
      subtitle={q.data ? headcount : 'Staff'}
      accessory={
        <Pressable onPress={() => router.push('/admin/employee-new')} hitSlop={6} accessibilityRole="button" accessibilityLabel="Add employee">
          <LinearGradient
            colors={gradients.coral}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={[{ width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, shadows.coral]}
          >
            <Icon name="plus" size={22} color={palette.white} />
          </LinearGradient>
        </Pressable>
      }
    >
      {/* search */}
      <View
        style={{
          height: 46,
          borderRadius: 14,
          backgroundColor: palette.surface,
          borderWidth: 1,
          borderColor: palette.line,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 10,
          paddingHorizontal: 14,
        }}
      >
        <Icon name="search" size={18} color={palette.faint} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          accessibilityLabel="Search staff"
          placeholder="Search name, ID or role"
          placeholderTextColor={palette.faint}
          returnKeyType="search"
          autoCorrect={false}
          style={[font(500), { flex: 1, fontSize: 13.5, color: palette.ink, padding: 0 }]}
        />
        {trimmed.length > 0 && (
          <Pressable onPress={() => setQuery('')} hitSlop={8} accessibilityRole="button" accessibilityLabel="Clear search">
            <Icon name="x" size={16} color={palette.faint} />
          </Pressable>
        )}
      </View>

      {/* list */}
      <View style={{ marginTop: 13 }}>
        <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
          {q.data &&
            (count === 0 ? (
              trimmed.length > 0 ? (
                <EmptyState
                  icon="search"
                  title={`No staff match "${trimmed}"`}
                  subtitle="Try a different name, ID or role."
                />
              ) : (
                <EmptyState
                  icon="users"
                  title="No team members yet"
                  subtitle="Add your first employee to start managing your team."
                  tone={{ color: palette.sage, bg: tint.sage }}
                  action={{ label: 'Add employee', icon: 'plus', onPress: () => router.push('/admin/employee-new') }}
                />
              )
            ) : (
              <View style={{ gap: 9 }}>
                {people.map((e) => (
              <Card
                key={e.id}
                padding={0}
                elevated={false}
                style={{ borderRadius: radius.lg, opacity: e.active ? 1 : 0.6 }}
                onPress={() =>
                  router.push({
                    pathname: '/admin/employee-edit',
                    // The hub fetches the full detail itself — pass only what it shows while loading.
                    params: { id: e.id, name: e.fullName, email: e.email, role: e.role },
                  })
                }
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, paddingHorizontal: 14 }}>
                  <Avatar initial={e.initial} size={42} tint={{ bg: tint.neutral, fg: palette.soft }} />
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                      {e.fullName}
                    </Text>
                    {/* Only the parts that exist — no stray separator, no empty line. */}
                    {(e.jobTitle || e.department) ? (
                      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]} numberOfLines={1}>
                        {[e.jobTitle, e.department].filter(Boolean).join(' · ')}
                      </Text>
                    ) : null}
                  </View>
                  {!e.active ? (
                    <Chip label="Inactive" size="sm" background={tint.neutral} color={palette.faint} />
                  ) : (
                    e.department && <Chip label={e.department} dot background={tint.neutral} color={palette.soft} />
                  )}
                </View>
              </Card>
                ))}
              </View>
            ))}
        </AsyncBoundary>
      </View>
    </CollapsingHeaderScreen>
  );
}
