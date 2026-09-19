import React, { ReactNode } from 'react';
import { View, Text, Pressable, ScrollView, StyleProp, ViewStyle } from 'react-native';
import Animated, {
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useSharedValue,
  interpolate,
  Extrapolation,
} from 'react-native-reanimated';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Icon } from '@/components/ui/Icon';
import { palette, spacing, font, radius } from '@/theme';

/**
 * Screen frame with a header that stays pinned to the top while the body
 * scrolls beneath it. Two modes:
 *
 *  • `collapsible` (default false) — the header shrinks as you scroll: the title
 *    font scales down, the eyebrow/subtitle fade + collapse, and the accessory
 *    (avatar/badge) scales down. Great for Home / Dashboard.
 *  • sticky (collapsible omitted) — the header is fixed at full size; only the
 *    body scrolls. Optional `headerExtra` (e.g. a day selector + date row) is
 *    pinned with it. Great for list pages like Schedule / Staff / Approvals.
 *
 * This is the standard way to add a sticky/collapsing header anywhere in the
 * app — reach for it instead of hand-rolling scroll math.
 */

type Props = {
  title: string;
  /** Small line below the title (collapses on scroll). */
  subtitle?: string;
  /** Small line ABOVE the title, e.g. a greeting (collapses on scroll). */
  eyebrow?: ReactNode;
  /** Right-side node (avatar / badge / chip). Scales down when collapsing. */
  accessory?: ReactNode;
  onAccessoryPress?: () => void;
  /** Show a back button that pops the stack. */
  back?: boolean;
  /** Larger title weight/size (matches ScreenHeader `large`). */
  large?: boolean;
  /** Base title size (defaults: large→21, else→20). */
  titleSize?: number;
  /** Enable the shrink-on-scroll animation. */
  collapsible?: boolean;
  /** Extra content pinned inside the header, below the title row (sticky mode). */
  headerExtra?: ReactNode;
  background?: string;
  barStyle?: 'dark' | 'light';
  paddingX?: number;
  /** Extra bottom padding (e.g. 70 to clear the floating tab bar). */
  bottomInset?: number;
  children: ReactNode;
  contentStyle?: StyleProp<ViewStyle>;
};

const AText = Animated.createAnimatedComponent(Text);

export function CollapsingHeaderScreen({
  title,
  subtitle,
  eyebrow,
  accessory,
  onAccessoryPress,
  back = false,
  large = false,
  titleSize,
  collapsible = false,
  headerExtra,
  background = palette.bg,
  barStyle = 'dark',
  paddingX = spacing.screenX,
  bottomInset = 0,
  children,
  contentStyle,
}: Props) {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const topPad = insets.top + 6;
  const padBottom = insets.bottom + bottomInset + 16;

  const baseTitleSize = titleSize ?? (large ? 21 : 20);
  const titleWeight = large ? font(800) : font(700);

  const BackButton = back ? (
    <Pressable
      onPress={() => (router.canGoBack() ? router.back() : null)}
      style={{
        width: 40,
        height: 40,
        borderRadius: radius.md,
        backgroundColor: palette.surface,
        borderWidth: 1,
        borderColor: palette.line,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon name="chevL" size={18} color={palette.ink} />
    </Pressable>
  ) : null;

  const Accessory = accessory ? (
    onAccessoryPress ? (
      <Pressable onPress={onAccessoryPress} hitSlop={8}>
        {accessory}
      </Pressable>
    ) : (
      accessory
    )
  ) : null;

  // ---- Sticky mode: header is child 0 of a ScrollView (RN pins it) ----
  if (!collapsible) {
    return (
      <View style={{ flex: 1, backgroundColor: background }}>
        <StatusBar style={barStyle} />
        <ScrollView
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          automaticallyAdjustKeyboardInsets
          stickyHeaderIndices={[0]}
          contentContainerStyle={{ paddingBottom: padBottom }}
        >
          <View style={{ backgroundColor: background, paddingTop: topPad, paddingHorizontal: paddingX, paddingBottom: 12 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              {BackButton}
              <View style={{ flex: 1, minWidth: 0 }}>
                {eyebrow}
                <Text style={[titleWeight, { fontSize: baseTitleSize, color: palette.ink, letterSpacing: -0.5 }]} numberOfLines={1}>
                  {title}
                </Text>
                {subtitle && (
                  <Text style={[font(500), { fontSize: 12.5, color: palette.faint, marginTop: 6 }]} numberOfLines={1}>
                    {subtitle}
                  </Text>
                )}
              </View>
              {Accessory}
            </View>
            {headerExtra}
          </View>
          <View style={{ paddingHorizontal: paddingX, paddingTop: 4 }}>
            <View style={contentStyle}>{children}</View>
          </View>
        </ScrollView>
      </View>
    );
  }

  // ---- Collapsible mode: animated absolute header + Reanimated scroll ----
  const hasEyebrow = !!eyebrow;
  const hasSub = !!subtitle;
  const EXPANDED = topPad + (hasEyebrow || hasSub ? 78 : 60);
  const COLLAPSED = topPad + 46;
  const DIST = EXPANDED - COLLAPSED;

  const scrollY = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler((e) => {
    scrollY.value = e.contentOffset.y;
  });

  const headerStyle = useAnimatedStyle(() => ({
    height: interpolate(scrollY.value, [0, DIST], [EXPANDED, COLLAPSED], Extrapolation.CLAMP),
  }));
  const titleStyle = useAnimatedStyle(() => ({
    fontSize: interpolate(scrollY.value, [0, DIST], [baseTitleSize, Math.round(baseTitleSize * 0.8)], Extrapolation.CLAMP),
  }));
  // Eyebrow (above title) and subtitle (below title) each collapse their own
  // height. Heights leave room for ascenders/descenders so nothing is sheared.
  const eyebrowStyle = useAnimatedStyle(() => ({
    opacity: interpolate(scrollY.value, [0, DIST * 0.5], [1, 0], Extrapolation.CLAMP),
    height: interpolate(scrollY.value, [0, DIST], [22, 0], Extrapolation.CLAMP),
  }));
  const subtitleStyle = useAnimatedStyle(() => ({
    opacity: interpolate(scrollY.value, [0, DIST * 0.5], [1, 0], Extrapolation.CLAMP),
    height: interpolate(scrollY.value, [0, DIST], [24, 0], Extrapolation.CLAMP),
  }));
  const accessoryStyle = useAnimatedStyle(() => ({
    transform: [{ scale: interpolate(scrollY.value, [0, DIST], [1, 0.78], Extrapolation.CLAMP) }],
  }));

  return (
    <View style={{ flex: 1, backgroundColor: background }}>
      <StatusBar style={barStyle} />

      <Animated.View
        style={[
          {
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            zIndex: 10,
            backgroundColor: background,
            paddingTop: topPad,
            paddingHorizontal: paddingX,
            justifyContent: 'flex-end',
            paddingBottom: 8,
          },
          headerStyle,
        ]}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          {BackButton}
          <View style={{ flex: 1, minWidth: 0 }}>
            {hasEyebrow && (
              <Animated.View style={[{ overflow: 'hidden', justifyContent: 'flex-end', paddingBottom: 3 }, eyebrowStyle]}>
                {eyebrow}
              </Animated.View>
            )}
            <AText
              style={[titleWeight, titleStyle, { color: palette.ink, letterSpacing: -0.5, lineHeight: Math.round(baseTitleSize * 1.32) }]}
              numberOfLines={1}
            >
              {title}
            </AText>
            {hasSub && (
              <Animated.View style={[{ overflow: 'hidden', justifyContent: 'flex-start', paddingTop: 3 }, subtitleStyle]}>
                <Text style={[font(500), { fontSize: 12.5, lineHeight: 16, color: palette.faint }]} numberOfLines={1}>
                  {subtitle}
                </Text>
              </Animated.View>
            )}
          </View>
          {Accessory && <Animated.View style={accessoryStyle}>{Accessory}</Animated.View>}
        </View>
      </Animated.View>

      <Animated.ScrollView
        onScroll={onScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="interactive"
        automaticallyAdjustKeyboardInsets
        contentContainerStyle={[{ paddingTop: EXPANDED + 6, paddingHorizontal: paddingX, paddingBottom: padBottom }, contentStyle]}
      >
        {children}
      </Animated.ScrollView>
    </View>
  );
}
