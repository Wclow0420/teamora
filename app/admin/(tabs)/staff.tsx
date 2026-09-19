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
  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      large
      title="Staff"
      subtitle={q.data ? `${count} ${count === 1 ? 'employee' : 'employees'}` : 'Staff'}
      accessory={
        <Pressable onPress={() => router.push('/admin/employee-new')} hitSlop={6}>
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
          placeholder="Search name, ID or role"
          placeholderTextColor={palette.faint}
          returnKeyType="search"
          autoCorrect={false}
          style={[font(500), { flex: 1, fontSize: 13.5, color: palette.ink, padding: 0 }]}
        />
        {trimmed.length > 0 && (
          <Pressable onPress={() => setQuery('')} hitSlop={8}>
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
                {q.data.map((e) => (
              <Card
                key={e.id}
                padding={0}
                elevated={false}
                style={{ borderRadius: radius.lg }}
                onPress={() =>
                  router.push({
                    pathname: '/admin/employee-edit',
                    params: { id: e.id, name: e.fullName, email: e.email, jobTitle: e.jobTitle ?? '', department: e.department ?? '', role: e.role, reportingManagerId: e.reportingManagerId ?? '', monthlySalary: e.monthlySalary != null ? String(e.monthlySalary) : '', maritalStatus: e.maritalStatus ?? '', spouseWorking: e.spouseWorking != null ? String(e.spouseWorking) : '', numChildren: String(e.numChildren) },
                  })
                }
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, paddingHorizontal: 14 }}>
                  <Avatar initial={e.initial} size={42} tint={{ bg: tint.neutral, fg: palette.soft }} />
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                      {e.fullName}
                    </Text>
                    <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]}>
                      {e.jobTitle} · {e.department}
                    </Text>
                  </View>
                  {e.department && <Chip label={e.department} dot background={tint.neutral} color={palette.soft} />}
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
