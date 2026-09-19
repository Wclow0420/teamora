# 2026-06-16 — Animated pill tab bar

## What changed

`PillTabBar` (`src/components/navigation/PillTabBar.tsx`) now animates tab
switches instead of snapping:

- Each tab item is an `AnimatedPressable` carrying a Reanimated `layout` spring
  (`LinearTransition.springify().damping(20).stiffness(180).mass(0.6)`), so when
  the focused tab becomes `flex: 1` and the previous one shrinks back to a 44px
  icon, the width/position change morphs smoothly.
- The coral-tint pill background is a separate `Animated.View` with
  `FadeIn`/`FadeOut` so the colour fades rather than pops as the pill expands.
- The active label fades in (slight delay) / out with the morph.
- Haptic selection feedback on tab press is unchanged.

## Notes

- Pure layout/opacity animation on the UI thread (Reanimated) — no measuring or
  shared-value wiring needed; React just re-renders with the new focused index
  and the `layout` transition handles the rest.
- Applies to both the staff and admin tab bars (they share this component).

## Verification

- `npx tsc --noEmit` → clean.
- `npx expo export --platform ios` → bundles successfully.
