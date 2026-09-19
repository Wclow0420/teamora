import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Animated, { LinearTransition, FadeIn, FadeOut } from 'react-native-reanimated';
import * as Haptics from 'expo-haptics';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { palette, radius, font, shadows, tint } from '@/theme';
import { Icon, IconName } from '@/components/ui/Icon';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/**
 * Floating pill bottom nav — the redesigned navigation the user asked for. The
 * active tab expands to show icon + label on a coral-tint pill; the rest stay
 * as clean icons. Drives both the staff and admin tab layouts.
 *
 * Switching tabs is animated: each item carries a Reanimated `layout` spring so
 * the active pill smoothly expands while the previous one collapses back to an
 * icon, and the label/background fade in and out. Each route declares its icon
 * via the custom `tabBarIconName` option (label comes from `options.title`).
 */
export function PillTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  // One spring config reused for every item so the expand/collapse stays in sync.
  const morph = LinearTransition.springify().damping(20).stiffness(180).mass(0.6);

  return (
    <View style={{ backgroundColor: palette.bg, paddingHorizontal: 14, paddingTop: 10, paddingBottom: Math.max(insets.bottom, 14) }}>
      <View
        style={[
          {
            flexDirection: 'row',
            alignItems: 'center',
            gap: 3,
            backgroundColor: palette.surface,
            borderWidth: 1,
            borderColor: palette.line,
            borderRadius: radius['3xl'],
            padding: 6,
          },
          shadows.float,
        ]}
      >
        {state.routes.map((route, index) => {
          const { options } = descriptors[route.key];
          const focused = state.index === index;
          const label = (options.title ?? route.name) as string;
          const iconName = ((options as any).tabBarIconName ?? 'home') as IconName;

          const onPress = () => {
            const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) {
              Haptics.selectionAsync().catch(() => {});
              navigation.navigate(route.name as never);
            }
          };

          return (
            <AnimatedPressable
              key={route.key}
              onPress={onPress}
              layout={morph}
              style={
                focused
                  ? {
                      flex: 1,
                      flexDirection: 'row',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 7,
                      height: 44,
                      borderRadius: radius.xl,
                    }
                  : { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }
              }
            >
              {/* coral pill background fades in/out so the colour doesn't pop */}
              {focused && (
                <Animated.View
                  entering={FadeIn.duration(220)}
                  exiting={FadeOut.duration(140)}
                  style={[StyleSheet.absoluteFillObject, { borderRadius: radius.xl, backgroundColor: tint.coral }]}
                />
              )}
              <Icon
                name={iconName}
                size={focused ? 21 : 22}
                color={focused ? palette.coral : palette.inactive}
                stroke={focused ? 2.1 : 1.9}
              />
              {focused && (
                <Animated.Text
                  entering={FadeIn.duration(200).delay(40)}
                  exiting={FadeOut.duration(120)}
                  style={[font(700), { fontSize: 12.5, color: palette.coral, letterSpacing: -0.1 }]}
                  numberOfLines={1}
                >
                  {label}
                </Animated.Text>
              )}
            </AnimatedPressable>
          );
        })}
      </View>
    </View>
  );
}
