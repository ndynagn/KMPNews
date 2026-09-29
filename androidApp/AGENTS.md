# Android client

Read [Kotlin style](../docs/standards/kotlin-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md).

- New presentation belongs in `feature/<name>/presentation` within this client.
  The current `App()` import from `sharedUI` is a transitional starter exception.
- Use Android lifecycle-owned ViewModels at screen/navigation boundaries. Expose
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
- Use Android resources for user-visible strings and the client's theme for
  typography/colors. Reuse tokens for repeated semantic dimensions; do not invent
  a full design system for one spacing value. Add semantics/content descriptions
  to meaningful controls and stable keys to stateful lists.
- Verify loading/empty/error/content and relevant retry actions. Check supported
  widths, text scaling and configuration changes for modified screens.
