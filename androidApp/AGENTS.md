# Android client

Read [Kotlin style](../docs/standards/kotlin-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md).

- Shared presentation belongs in sharedUI; read its [rules](../sharedUI/AGENTS.md).
  This host owns Android entry points, permissions, intents, lifecycle integration
  and platform-only adapters. Keep such feature code in `feature/<name>`.
- Bind shared Compose ViewModels to Android screen/navigation lifecycle owners. Expose
  immutable UI state; collect flows with lifecycle awareness. Do not retain an
  Activity, View, Context or navigation controller in a ViewModel.
- Split a lifecycle-aware route/container from a state-and-callback screen when
  the screen needs a ViewModel. Reusable components accept only needed values
  and callbacks, not the entire ViewModel.
- Do not load data during composition. Use an owned lifecycle/effect trigger;
  define keys, repeatability and cancellation. Local visual toggles may use local
  Compose state; business state belongs in the ViewModel/shared domain.
- UI-emitting Composables use PascalCase and a `Modifier` parameter where useful,
  defaulting to `Modifier`. Preserve caller modifiers and explicit state ownership.
- Use Android resources for platform-only strings and shared Compose resources for
  common screens. Use the appropriate theme for typography/colors.
  Reuse tokens for repeated semantic dimensions; do not invent
  a full design system for one spacing value. Add semantics/content descriptions
  to meaningful controls and stable keys to stateful lists.
- Verify loading/empty/error/content and relevant retry actions. Check supported
  widths, text scaling and configuration changes for modified screens.

Additional review routing: apply [Kotlin naming and organization](../docs/standards/kotlin-style.md#compose-and-android-resource-naming)
for this scope and [test conventions](../docs/standards/verification.md#test-and-fixture-conventions)
for its fixtures and verification code.
