# 2026-06-18 — App-wide keyboard handling (standard)

## What

Made "focused input stays above the keyboard" automatic and consistent across
every screen, by baking it into the shared frames instead of per-screen.

## How
- `Screen` (the frame all forms use): the scroll container now sets
  `keyboardShouldPersistTaps="handled"`, `keyboardDismissMode="interactive"`,
  and `automaticallyAdjustKeyboardInsets` (iOS: auto bottom-inset + scroll the
  focused field into view). On Android it's wrapped in a `KeyboardAvoidingView`
  (`behavior="height"`) since `automaticallyAdjustKeyboardInsets` is iOS-only.
- `CollapsingHeaderScreen`: same keyboard props added to both its scroll views
  (sticky + collapsible) for consistency.

## Result
- Typing in any field keeps it visible above the keyboard; tapping outside or
  dragging the list dismisses it. Same behaviour on every form (login, register,
  apply-leave, claim, add/edit employee, company settings).

## Convention (now in CLAUDE.md §11)
Build forms with `Screen` + the form kit; never add a per-screen
`KeyboardAvoidingView`.

## Notes / verification
- `npx tsc --noEmit` clean; `npx expo export` bundles.
- Kept it Expo Go-compatible (no native keyboard libs like
  react-native-keyboard-controller, which aren't in Expo Go). If we later move to
  a dev client, `KeyboardAwareScrollView` could make the Android path as smooth
  as iOS — swap it inside `Screen` and every form benefits automatically.
