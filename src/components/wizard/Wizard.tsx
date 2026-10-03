/**
 * Wizard — a calm, one-decision-per-screen flow (think "set up your new iPhone").
 *
 *   const steps: WizardStep[] = [
 *     { key: 'name', title: 'What should we call you?', icon: 'user',
 *       render: () => <TextField … />,
 *       canContinue: name.trim().length > 0,
 *       onContinue: async () => { await save(name); } },          // throw → message shown above the button
 *     { key: 'team', title: 'Invite your team', optional: true,   // adds "Skip"
 *       render: (ctx) => …, primaryLabel: 'Send invites' },
 *   ];
 *   <Wizard steps={steps} onFinish={() => router.replace('/home')} onExit={() => router.back()} />
 *
 * Step fields:
 *   key            stable id (also used by `ctx.goTo(key)`)
 *   title          large title; subtitle = one plain sentence under it
 *   icon | hero    hero above the title: an icon in a tinted tile (springs in), or any custom node
 *   bare           the body draws its own heading (e.g. `WizardCelebration`); title is still
 *                  announced to screen readers
 *   optional       shows "Skip" in the header (skips without running onContinue)
 *   noBack         hides Back on this step (e.g. right after an irreversible create)
 *   hideProgress   hides the progress bar (welcome screens)
 *   primaryLabel   button text, default "Continue"
 *   canContinue    false → the button is disabled
 *   onContinue     async work before advancing. Return `false` to stay (e.g. it navigated with
 *                  `ctx.goTo`, or wants the person to review something). A thrown error's
 *                  message (ApiError → the server's sentence) is shown above the button.
 *   render(ctx)    the body. ctx = { next, back, goTo, index, total, busy }
 *
 * Wizard props: `onFinish` runs after the last step's onContinue; `onExit` is Back on the first
 * step (or on any step whose earlier steps can't be revisited); `progress` = 'bar' | 'dots';
 * `initialStep` = key to open on.
 *
 * Motion: steps slide/fade sideways (forward → in from the right; back → from the left) with
 * the hero → title → subtitle → body staggered ~70 ms apart; the progress bar springs. Reduce
 * Motion → crossfades only (see ./motion.ts). Android hardware Back = wizard Back.
 *
 * Keyboard: the Wizard can't use `Screen` (its button is pinned below the scrolling body), so
 * — the one sanctioned exception to "never add your own KeyboardAvoidingView" — it wraps body +
 * footer in a KeyboardAvoidingView, and scrolls the focused field into view once the keyboard
 * is up. The button rides just above the keyboard.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  BackHandler,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type HostInstance,
} from 'react-native';
import Animated, { FadeIn, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button, Icon, IconTile, type IconName } from '@/components/ui';
import { errorMessage } from '@/lib/errors';
import { palette, radius, type, tint, spacing } from '@/theme';
import { STAGGER, popIn, riseIn, stepEntering, stepExiting } from './motion';
import { WizardProgress } from './WizardProgress';

export type WizardCtx = {
  next: () => void;
  back: () => void;
  goTo: (key: string) => void;
  index: number;
  total: number;
  busy: boolean;
};

export type WizardStep = {
  key: string;
  title: string;
  subtitle?: string;
  icon?: IconName;
  /** Accent for the icon hero. Defaults to coral. */
  iconTone?: { color: string; bg: string };
  hero?: React.ReactNode;
  bare?: boolean;
  optional?: boolean;
  noBack?: boolean;
  hideProgress?: boolean;
  primaryLabel?: string;
  render: (ctx: WizardCtx) => React.ReactNode;
  canContinue?: boolean;
  onContinue?: (ctx: WizardCtx) => Promise<boolean | void>;
};

type Props = {
  steps: WizardStep[];
  initialStep?: string;
  onFinish: () => void | Promise<void>;
  onExit?: () => void;
  progress?: 'bar' | 'dots';
};

const PAD_X = 24;

export function Wizard({ steps, initialStep, onFinish, onExit, progress = 'bar' }: Props) {
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotion();

  const [index, setIndex] = useState(() => {
    const i = initialStep ? steps.findIndex((s) => s.key === initialStep) : 0;
    return i < 0 ? 0 : i;
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [keyboardUp, setKeyboardUp] = useState(false);

  // Clamp if the step list shrinks under us.
  const safeIndex = Math.min(index, steps.length - 1);
  const step = steps[safeIndex];
  const total = steps.length;

  // +1 forward / −1 back / 0 first mount. A shared value so the layout-animation
  // worklets read the direction at the moment they run (see motion.ts).
  const dir = useSharedValue(0);
  const entering = useMemo(() => stepEntering(dir, reduce), [dir, reduce]);
  const exiting = useMemo(() => stepExiting(dir, reduce), [dir, reduce]);
  // Content stagger — memoised so typing in a field doesn't rebuild the worklets.
  const anim = useMemo(
    () => ({
      hero: popIn(STAGGER.hero, reduce),
      title: riseIn(STAGGER.title, reduce),
      subtitle: riseIn(STAGGER.subtitle, reduce),
      body: riseIn(STAGGER.body, reduce),
      bareBody: riseIn(0, reduce),
    }),
    [reduce],
  );

  const busyRef = useRef(false);

  const move = useCallback(
    (to: number) => {
      if (to < 0 || to >= steps.length || to === safeIndex) return;
      dir.value = to > safeIndex ? 1 : -1;
      Keyboard.dismiss();
      setError(null);
      setIndex(to);
    },
    [steps.length, safeIndex, dir],
  );

  const canGoBack = safeIndex > 0 && !step.noBack;

  const back = useCallback(() => {
    if (busyRef.current) return;
    if (canGoBack) move(safeIndex - 1);
    else onExit?.();
  }, [canGoBack, move, safeIndex, onExit]);

  const next = useCallback(() => move(safeIndex + 1), [move, safeIndex]);

  const goTo = useCallback(
    (key: string) => {
      const i = steps.findIndex((s) => s.key === key);
      if (i >= 0) move(i);
    },
    [steps, move],
  );

  const ctx: WizardCtx = { next, back, goTo, index: safeIndex, total, busy };

  const advance = async () => {
    if (busyRef.current) return;
    setError(null);
    busyRef.current = true;
    setBusy(true);
    try {
      const result = step.onContinue ? await step.onContinue(ctx) : undefined;
      if (result === false) return;
      if (safeIndex >= steps.length - 1) {
        await onFinish();
      } else {
        next();
      }
    } catch (e) {
      // A step's own `throw new Error('…')` is written for people — show it. Anything
      // else (ApiError → the server's sentence; a fetch TypeError → the offline hint).
      setError(e instanceof Error && e.name === 'Error' && e.message ? e.message : errorMessage(e));
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const skip = () => {
    if (busyRef.current) return;
    Haptics.selectionAsync().catch(() => {});
    if (safeIndex >= steps.length - 1) void onFinish();
    else next();
  };

  // Android hardware Back walks the wizard, never silently leaves mid-flow.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      back();
      return true;
    });
    return () => sub.remove();
  }, [back]);

  // Keyboard: track visibility (footer padding) and keep the focused field visible.
  const scrollRef = useRef<ScrollView>(null);
  const contentRef = useRef<View>(null);
  const scrollY = useRef(0);
  const viewportH = useRef(0);
  useEffect(() => {
    const showEvt = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvt = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const show = Keyboard.addListener(showEvt, () => setKeyboardUp(true));
    const hide = Keyboard.addListener(hideEvt, () => setKeyboardUp(false));
    const shown = Keyboard.addListener('keyboardDidShow', () => {
      const input = TextInput.State.currentlyFocusedInput() as HostInstance | null;
      const content = contentRef.current;
      if (!input || !content) return;
      input.measureLayout(
        content,
        (_x, y, _w, h) => {
          const margin = 28;
          const bottom = y + h + margin;
          const visibleBottom = scrollY.current + viewportH.current;
          if (bottom > visibleBottom) scrollRef.current?.scrollTo({ y: bottom - viewportH.current, animated: true });
          else if (y - margin < scrollY.current) scrollRef.current?.scrollTo({ y: Math.max(0, y - margin), animated: true });
        },
        () => {},
      );
    });
    return () => {
      show.remove();
      hide.remove();
      shown.remove();
    };
  }, []);

  // Progress fades out on welcome-style steps but stays mounted, so the fill
  // animates from where it was rather than popping in.
  const progressOpacity = useSharedValue(step.hideProgress ? 0 : 1);
  useEffect(() => {
    progressOpacity.value = withTiming(step.hideProgress ? 0 : 1, { duration: reduce ? 0 : 220 });
  }, [step.hideProgress, reduce, progressOpacity]);
  const progressStyle = useAnimatedStyle(() => ({ opacity: progressOpacity.value }));

  const showBack = canGoBack || !!onExit;
  const isLast = safeIndex >= steps.length - 1;
  const label = step.primaryLabel ?? (isLast ? 'Done' : 'Continue');
  const tone = step.iconTone ?? { color: palette.coral, bg: tint.coral };

  return (
    <View style={{ flex: 1, backgroundColor: palette.bg, paddingTop: insets.top }}>
      <StatusBar style="dark" />

      {/* Header: back · progress · skip — fixed; only the step below moves. */}
      <View
        style={{ height: 52, flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: spacing.lg }}
        accessibilityLabel={`Step ${safeIndex + 1} of ${total}`}
      >
        <View style={{ width: 64 }}>
          {showBack && (
            <Pressable
              onPress={back}
              disabled={busy}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel={canGoBack ? 'Back' : 'Close'}
              style={({ pressed }) => ({
                width: 40,
                height: 40,
                borderRadius: radius.md,
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: pressed ? palette.surfaceSunken : palette.surface,
                borderWidth: 1,
                borderColor: palette.line,
                opacity: busy ? 0.5 : 1,
              })}
            >
              <Icon name={canGoBack ? 'chevL' : 'x'} size={18} color={palette.ink} />
            </Pressable>
          )}
        </View>
        <Animated.View style={[{ flex: 1 }, progressStyle]}>
          <WizardProgress index={safeIndex} total={total} variant={progress} reduce={reduce} />
        </Animated.View>
        <View style={{ width: 64, alignItems: 'flex-end' }}>
          {step.optional && (
            <Pressable onPress={skip} disabled={busy} hitSlop={10} accessibilityRole="button" accessibilityLabel="Skip this step">
              <Text style={[type.label, { fontSize: 14, color: busy ? palette.inactive : palette.soft }]}>Skip</Text>
            </Pressable>
          )}
        </View>
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        {/* Stage: the keyed step view swaps with entering/exiting layout animations. */}
        <View style={{ flex: 1, overflow: 'hidden' }}>
          <Animated.View key={step.key} entering={entering} exiting={exiting} style={StyleSheet.absoluteFill}>
            <ScrollView
              ref={scrollRef}
              showsVerticalScrollIndicator={false}
              keyboardShouldPersistTaps="handled"
              keyboardDismissMode="interactive"
              scrollEventThrottle={32}
              onScroll={(e) => {
                scrollY.current = e.nativeEvent.contentOffset.y;
              }}
              onLayout={(e) => {
                viewportH.current = e.nativeEvent.layout.height;
              }}
              contentContainerStyle={{ flexGrow: 1, paddingHorizontal: PAD_X, paddingTop: 12, paddingBottom: 28 }}
            >
              <View ref={contentRef} collapsable={false}>
                {step.bare ? (
                  <Text accessibilityRole="header" style={visuallyHidden}>
                    {step.title}
                  </Text>
                ) : (
                  <>
                    {(step.hero || step.icon) && (
                      <Animated.View entering={anim.hero} style={{ marginBottom: 22, alignSelf: 'flex-start' }}>
                        {step.hero ?? (
                          <IconTile icon={step.icon!} color={tone.color} background={tone.bg} size={68} iconSize={32} cornerRadius={22} />
                        )}
                      </Animated.View>
                    )}
                    <Animated.Text
                      entering={anim.title}
                      accessibilityRole="header"
                      style={[type.display, { fontSize: 31, lineHeight: 38, color: palette.ink }]}
                    >
                      {step.title}
                    </Animated.Text>
                    {!!step.subtitle && (
                      <Animated.Text
                        entering={anim.subtitle}
                        style={[type.body, { fontSize: 15.5, lineHeight: 23, color: palette.soft, marginTop: 10 }]}
                      >
                        {step.subtitle}
                      </Animated.Text>
                    )}
                  </>
                )}
                <Animated.View entering={step.bare ? anim.bareBody : anim.body} style={{ marginTop: step.bare ? 0 : 28 }}>
                  {step.render(ctx)}
                </Animated.View>
              </View>
            </ScrollView>
          </Animated.View>
        </View>

        {/* Footer: the one action, pinned above the home indicator (or the keyboard). */}
        <View
          style={{
            paddingHorizontal: PAD_X,
            paddingTop: 12,
            paddingBottom: keyboardUp ? 12 : Math.max(insets.bottom, 16),
            backgroundColor: palette.bg,
          }}
        >
          {!!error && (
            <Animated.View
              entering={FadeIn.duration(180)}
              accessibilityLiveRegion="polite"
              style={{
                flexDirection: 'row',
                alignItems: 'flex-start',
                gap: 8,
                marginBottom: 12,
                paddingVertical: 10,
                paddingHorizontal: 12,
                borderRadius: radius.md,
                backgroundColor: tint.danger,
              }}
            >
              <Icon name="shield" size={16} color={palette.danger} />
              <Text style={[type.meta, { flex: 1, fontSize: 13, lineHeight: 18, color: palette.danger }]}>{error}</Text>
            </Animated.View>
          )}
          <Button
            label={label}
            height={54}
            onPress={advance}
            loading={busy}
            disabled={step.canContinue === false}
          />
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

/** Present for VoiceOver, invisible on screen. */
const visuallyHidden = { position: 'absolute' as const, width: 1, height: 1, opacity: 0 };
